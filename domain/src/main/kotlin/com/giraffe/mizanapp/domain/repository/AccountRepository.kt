package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.identity.SignOutMode
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Access to the account session — sign-in, sign-out and the display name.
 *
 * Declared here, implemented in `:data` over Supabase auth. No method throws for
 * an ordinary outcome: offline, an expired code, a wrong code and a foreign
 * account signing in are all values, not exceptions.
 */
interface AccountRepository {

    /** The current session. Emits immediately and on every change, including silent renewal. */
    fun observeSession(): Flow<AccountSession>

    /**
     * Asks the account service to send a one-time code to [email].
     *
     * Never stores, sets, or transmits a password — there is none in this product (FR-002).
     * Offline returns [CodeRequest.NeedsConnection]; every local record is untouched either way.
     */
    suspend fun requestCode(email: String): CodeRequest

    /**
     * Completes sign-in. On success the session becomes SignedIn and the caller is
     * responsible for triggering migration ([SyncRepository.syncNow]).
     *
     * An expired or incorrect code is an ordinary outcome, not an exception, and never
     * discards the entered address or any local record (FR-002a).
     *
     * [replaceLocalRecords] MUST be left false on the first call. When the device holds a
     * different account's records this returns [CodeConfirmation.WouldReplaceLocalRecords]
     * and opens no session; the caller obtains the confirmation FR-013a requires and calls
     * again with true. There is no other way to remove a prior account's records.
     */
    suspend fun confirmCode(
        email: String,
        code: String,
        replaceLocalRecords: Boolean = false,
    ): CodeConfirmation

    /**
     * Ends the session.
     *
     * [SignOutMode.KEEP_LOCAL_RECORDS] leaves every record on the device, fully usable
     * signed-out (FR-007a). [SignOutMode.REMOVE_LOCAL_RECORDS] additionally clears them
     * (FR-007b). Neither removes anything from the account (FR-007d).
     *
     * Callers MUST have surfaced [SyncRepository.observePendingCount] first when it is
     * non-zero (FR-007c); this method does not prompt.
     */
    suspend fun signOut(mode: SignOutMode)

    /** Optional, empty by default, editable at any time, never required (FR-007e). */
    suspend fun updateDisplayName(name: String?)

    /**
     * Local-only counts behind a removing sign-out's confirmation (FR-007b) —
     * how many recorded days and completions are about to leave this device.
     * Reads nothing from the account and changes nothing.
     */
    suspend fun localRecordCounts(): LocalRecordCounts
}

data class LocalRecordCounts(val recordedDays: Int, val completionCount: Int)

sealed interface CodeRequest {
    data class Sent(val resendAvailableAt: Instant) : CodeRequest
    data object NeedsConnection : CodeRequest
    data class AddressNotAccepted(val reason: String) : CodeRequest
}

sealed interface CodeConfirmation {
    data class SignedIn(val session: AccountSession.SignedIn) : CodeConfirmation
    data class NotAccepted(val reason: CodeRejection) : CodeConfirmation
    data object NeedsConnection : CodeConfirmation

    /**
     * A different account is signing into a device that holds this one's records.
     * No session was opened and nothing was changed. The caller MUST obtain the
     * confirmation FR-013a requires — naming [currentEmail], [recordedDays],
     * [completionCount] and [unsyncedCount] — before calling
     * [AccountRepository.confirmCode] again with `replaceLocalRecords = true` (R8).
     */
    data class WouldReplaceLocalRecords(
        val currentEmail: String,
        val recordedDays: Int,
        val completionCount: Int,
        val unsyncedCount: Int,
    ) : CodeConfirmation
}
