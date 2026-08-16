package com.giraffe.mizanapp.data.repository

import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.createSupabaseClient
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow

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
 */
class SupabaseAccountRepository(
    private val client: SupabaseClient?,
    private val accountScope: AccountScope,
) : AccountRepository {

    override fun observeSession(): Flow<AccountSession> {
        TODO("T062")
    }

    override suspend fun requestCode(email: String): CodeRequest {
        TODO("T062")
    }

    override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation {
        TODO("T062")
    }

    override suspend fun signOut(mode: SignOutMode) {
        TODO("T062")
    }

    override suspend fun updateDisplayName(name: String?) {
        TODO("T062")
    }
}

/**
 * The Koin-facing factory. Its return type names only [AccountRepository], so
 * `:app`'s DI wiring never needs `SupabaseClient` on its classpath — the same
 * boundary trick as [createRemoteDataSource].
 */
fun createAccountRepository(accountScope: AccountScope): AccountRepository =
    SupabaseAccountRepository(createSupabaseClient(), accountScope)
