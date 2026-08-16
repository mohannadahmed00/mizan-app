package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmSignInCodeTest {

    private class FakeAccountRepository(private val outcome: CodeConfirmation) : AccountRepository {
        override fun observeSession(): Flow<AccountSession> = flowOf(AccountSession.SignedOut)
        override suspend fun requestCode(email: String): CodeRequest = error("not used")
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean) = outcome
        override suspend fun signOut(mode: com.giraffe.mizanapp.domain.identity.SignOutMode) = error("not used")
        override suspend fun updateDisplayName(name: String?) = error("not used")
    }

    private class CountingSyncRepository : SyncRepository {
        var syncNowCalls = 0
            private set
        override fun observeStatus(): Flow<SyncStatus> = flowOf(SyncStatus.UpToDate)
        override fun observePendingCount(): Flow<Int> = flowOf(0)
        override fun syncNow() { syncNowCalls++ }
    }

    private val email = "user@example.test"

    @Test
    fun `a correct code returns SignedIn and syncs exactly once`() = runTest {
        val session = AccountSession.SignedIn(userId = "u-1", email = email)
        val accounts = FakeAccountRepository(CodeConfirmation.SignedIn(session))
        val sync = CountingSyncRepository()

        val result = ConfirmSignInCode(accounts, sync).invoke(email, "123456")

        assertEquals(CodeConfirmation.SignedIn(session), result)
        assertEquals(1, sync.syncNowCalls)
    }

    @Test
    fun `an expired code returns NotAccepted and never syncs`() = runTest {
        val accounts = FakeAccountRepository(CodeConfirmation.NotAccepted(CodeRejection.EXPIRED))
        val sync = CountingSyncRepository()

        val result = ConfirmSignInCode(accounts, sync).invoke(email, "000000")

        assertEquals(CodeConfirmation.NotAccepted(CodeRejection.EXPIRED), result)
        assertEquals(0, sync.syncNowCalls)
    }

    @Test
    fun `an incorrect code returns NotAccepted and never syncs`() = runTest {
        val accounts = FakeAccountRepository(CodeConfirmation.NotAccepted(CodeRejection.INCORRECT))
        val sync = CountingSyncRepository()

        val result = ConfirmSignInCode(accounts, sync).invoke(email, "000000")

        assertEquals(CodeConfirmation.NotAccepted(CodeRejection.INCORRECT), result)
        assertEquals(0, sync.syncNowCalls)
    }

    @Test
    fun `WouldReplaceLocalRecords opens no session and never syncs`() = runTest {
        val outcome = CodeConfirmation.WouldReplaceLocalRecords(
            currentEmail = "old@example.test",
            recordedDays = 30,
            completionCount = 90,
            unsyncedCount = 3,
        )
        val accounts = FakeAccountRepository(outcome)
        val sync = CountingSyncRepository()

        val result = ConfirmSignInCode(accounts, sync).invoke(email, "123456")

        assertEquals(outcome, result)
        assertEquals(0, sync.syncNowCalls)
    }

    @Test
    fun `an offline attempt returns NeedsConnection and never syncs`() = runTest {
        val accounts = FakeAccountRepository(CodeConfirmation.NeedsConnection)
        val sync = CountingSyncRepository()

        val result = ConfirmSignInCode(accounts, sync).invoke(email, "123456")

        assertEquals(CodeConfirmation.NeedsConnection, result)
        assertFalse(sync.syncNowCalls > 0)
    }

    @Test
    fun `no outcome throws`() = runTest {
        for (outcome in listOf(
            CodeConfirmation.SignedIn(AccountSession.SignedIn(userId = "u-1", email = email)),
            CodeConfirmation.NotAccepted(CodeRejection.EXPIRED),
            CodeConfirmation.NeedsConnection,
        )) {
            val result = ConfirmSignInCode(FakeAccountRepository(outcome), CountingSyncRepository())
                .invoke(email, "123456")
            assertTrue(result == outcome)
        }
    }
}
