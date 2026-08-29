package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.LocalRecordWipe
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.createSupabaseClient
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.LocalRecordCounts
import com.giraffe.mizanapp.domain.time.TimeProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

/**
 * [AccountRepository] over Supabase auth and [AccountScope].
 *
 * `requestCode` uses `signInWith(OTP) { createUser = true }` — sign-up and
 * sign-in are the same action (FR-001); there is no `signInWith(Email)` call
 * and no password parameter anywhere (FR-002). `observeSession` combines
 * Supabase's own session status with [AccountScope], restoring across process
 * death (FR-005). A missing client — no Supabase configuration — maps every
 * network-shaped outcome to [CodeRequest.NeedsConnection] /
 * [CodeConfirmation.NeedsConnection] rather than crashing (FR-003).
 *
 * A different account signing into a device already carrying one's records is
 * detected entirely locally, from [AccountScope] alone — no code needs to be
 * verified to know that (research R8, FR-013a).
 */
class SupabaseAccountRepository(
    private val client: SupabaseClient?,
    private val accountScope: AccountScope,
    private val db: MizanDatabase,
    private val outbox: Outbox,
    private val time: TimeProvider,
) : AccountRepository {

    private val wipe = LocalRecordWipe(db)

    override fun observeSession(): Flow<AccountSession> {
        val c = client ?: return flowOf(AccountSession.SignedOut)
        return combine(c.auth.sessionStatus, accountScope.observe()) { status, scoped ->
            val user = (status as? SessionStatus.Authenticated)?.session?.user
            if (user == null) {
                AccountSession.SignedOut
            } else {
                AccountSession.SignedIn(
                    userId = user.id,
                    email = user.email ?: (scoped as? AccountSession.SignedIn)?.email.orEmpty(),
                    displayName = (scoped as? AccountSession.SignedIn)?.displayName,
                )
            }
        }
    }

    override suspend fun requestCode(email: String): CodeRequest {
        val c = client ?: return CodeRequest.NeedsConnection
        return try {
            c.auth.signInWith(OTP) {
                this.email = email
                createUser = true
            }
            CodeRequest.Sent(resendAvailableAt = time.now().plus(RESEND_WAIT))
        } catch (e: HttpRequestException) {
            CodeRequest.NeedsConnection
        } catch (e: RestException) {
            if (e.statusCode in 500..599) CodeRequest.NeedsConnection else CodeRequest.AddressNotAccepted(e.message ?: e.error)
        } catch (e: Exception) {
            CodeRequest.NeedsConnection
        }
    }

    override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation {
        // Detected entirely locally, from AccountScope alone (research R8) — this
        // has to run before the client check below, or a build with no Supabase
        // configuration could never surface it at all.
        val existing = accountScope.current()
        if (existing is AccountSession.SignedIn && !existing.email.equals(email, ignoreCase = true) && !replaceLocalRecords) {
            val counts = localRecordCounts()
            return CodeConfirmation.WouldReplaceLocalRecords(
                currentEmail = existing.email,
                recordedDays = counts.recordedDays,
                completionCount = counts.completionCount,
                unsyncedCount = db.outboxDao().count(),
            )
        }

        val c = client ?: return CodeConfirmation.NeedsConnection

        return try {
            when (val result = c.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email, token = code)) {
                is OtpVerifyResult.Authenticated -> {
                    val userId = result.session.user?.id ?: return CodeConfirmation.NeedsConnection
                    if (replaceLocalRecords && existing is AccountSession.SignedIn) {
                        wipe.wipe()
                    }
                    accountScope.set(userId, email, null)
                    CodeConfirmation.SignedIn(AccountSession.SignedIn(userId = userId, email = email))
                }
                is OtpVerifyResult.VerifiedNoSession -> CodeConfirmation.NeedsConnection
            }
        } catch (e: AuthRestException) {
            val reason = if (e.errorCode == AuthErrorCode.OtpExpired) CodeRejection.EXPIRED else CodeRejection.INCORRECT
            CodeConfirmation.NotAccepted(reason)
        } catch (e: HttpRequestException) {
            CodeConfirmation.NeedsConnection
        } catch (e: Exception) {
            CodeConfirmation.NeedsConnection
        }
    }

    override suspend fun signOut(mode: SignOutMode) {
        client?.auth?.signOut()
        if (mode == SignOutMode.REMOVE_LOCAL_RECORDS) wipe.wipe()
    }

    override suspend fun updateDisplayName(name: String?) {
        accountScope.setDisplayName(name)
        val session = accountScope.current()
        if (session is AccountSession.SignedIn) {
            outbox.enqueue(
                OutboxEntry(
                    entityType = OutboxEntry.EntityType.PROFILE,
                    entityId = session.userId,
                    operation = OutboxEntry.Operation.UPSERT,
                    payload = Json.encodeToString(RemoteProfile(id = session.userId, displayName = name)),
                ),
            )
        }
    }

    override suspend fun localRecordCounts(): LocalRecordCounts = LocalRecordCounts(
        recordedDays = db.dayPlanDao().countPlans(),
        completionCount = db.completionDao().countAll(),
    )

    private companion object {
        val RESEND_WAIT: Duration = Duration.ofSeconds(30)
    }
}

/**
 * The Koin-facing factory. Its return type names only [AccountRepository], so
 * `:app`'s DI wiring never needs `SupabaseClient` on its classpath — the same
 * boundary trick as `createRemoteDataSource`.
 */
fun createAccountRepository(
    accountScope: AccountScope,
    db: MizanDatabase,
    outbox: Outbox,
    time: TimeProvider,
): AccountRepository = SupabaseAccountRepository(createSupabaseClient(), accountScope, db, outbox, time)
