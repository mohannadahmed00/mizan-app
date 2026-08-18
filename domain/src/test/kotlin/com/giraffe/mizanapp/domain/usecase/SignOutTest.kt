package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.LocalRecordCounts
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-007c: both modes report the pending count before acting. FR-007b/d: only
 * `REMOVE_LOCAL_RECORDS` ever reaches the wipe, and neither mode ever removes
 * anything from the account itself.
 */
class SignOutTest {

    /**
     * `signOut` is the only method this fake implements with a real body — the
     * usecase under test has no other way to reach the account, so a `signOut`
     * call is the only path capable of removing anything remote, and this fake
     * only ever records which mode it was asked for.
     */
    private class RecordingAccountRepository : AccountRepository {
        val signOutCalls = mutableListOf<SignOutMode>()

        override fun observeSession(): Flow<AccountSession> = flowOf(AccountSession.SignedOut)
        override suspend fun requestCode(email: String): CodeRequest = error("not used")
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation =
            error("not used")

        override suspend fun signOut(mode: SignOutMode) {
            signOutCalls += mode
        }

        override suspend fun updateDisplayName(name: String?) = error("not used")
        override suspend fun localRecordCounts(): LocalRecordCounts = error("not used")
    }

    private class FixedPendingSyncRepository(private val pending: Int) : SyncRepository {
        override fun observeStatus(): Flow<SyncStatus> = flowOf(SyncStatus.NotSignedIn)
        override fun observePendingCount(): Flow<Int> = flowOf(pending)
        override fun syncNow() = error("not used")
    }

    @Test
    fun keep_local_records_reports_the_pending_count_and_never_reaches_the_wipe() = runTest {
        val accounts = RecordingAccountRepository()
        val sync = FixedPendingSyncRepository(pending = 7)

        val outcome = SignOut(accounts, sync).invoke(SignOutMode.KEEP_LOCAL_RECORDS)

        assertEquals(7, outcome.pendingCount)
        assertEquals(listOf(SignOutMode.KEEP_LOCAL_RECORDS), accounts.signOutCalls)
    }

    @Test
    fun remove_local_records_reports_the_pending_count_and_reaches_the_wipe() = runTest {
        val accounts = RecordingAccountRepository()
        val sync = FixedPendingSyncRepository(pending = 3)

        val outcome = SignOut(accounts, sync).invoke(SignOutMode.REMOVE_LOCAL_RECORDS)

        assertEquals(3, outcome.pendingCount)
        assertEquals(listOf(SignOutMode.REMOVE_LOCAL_RECORDS), accounts.signOutCalls)
    }

    @Test
    fun the_pending_count_is_read_before_signOut_acts_not_after() = runTest {
        // A removing sign-out clears the outbox that this count is drawn from —
        // reading it after would always report zero, defeating FR-007c.
        var signedOut = false
        val accounts = object : AccountRepository by RecordingAccountRepository() {
            override suspend fun signOut(mode: SignOutMode) {
                signedOut = true
            }
        }
        val sync = object : SyncRepository {
            override fun observeStatus(): Flow<SyncStatus> = flowOf(SyncStatus.NotSignedIn)
            override fun observePendingCount(): Flow<Int> {
                assertFalse("pending count must be read before signOut() runs", signedOut)
                return flowOf(5)
            }
            override fun syncNow() = error("not used")
        }

        val outcome = SignOut(accounts, sync).invoke(SignOutMode.REMOVE_LOCAL_RECORDS)

        assertEquals(5, outcome.pendingCount)
        assertTrue(signedOut)
    }
}
