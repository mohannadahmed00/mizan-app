package com.giraffe.mizanapp.auth

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.identity.SignInStep
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.usecase.ConfirmSignInCode
import com.giraffe.mizanapp.domain.usecase.RequestSignInCode
import com.giraffe.mizanapp.today.FakeClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var clock: FakeClock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class ScriptedAccountRepository : AccountRepository {
        var nextCodeRequest: CodeRequest = CodeRequest.Sent(Instant.parse("2026-03-14T09:00:30Z"))
        var nextCodeConfirmation: CodeConfirmation = CodeConfirmation.SignedIn(
            AccountSession.SignedIn(userId = "u-1", email = "user@example.test"),
        )
        var lastRequestedEmail: String? = null
        var lastConfirmedCode: String? = null
        var requestCodeCallCount = 0

        override fun observeSession(): Flow<AccountSession> = flowOf(AccountSession.SignedOut)
        override suspend fun requestCode(email: String): CodeRequest {
            requestCodeCallCount++
            lastRequestedEmail = email
            return nextCodeRequest
        }
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation {
            lastConfirmedCode = code
            return nextCodeConfirmation
        }
        override suspend fun signOut(mode: SignOutMode) = error("not used")
        override suspend fun updateDisplayName(name: String?) = error("not used")
        override suspend fun localRecordCounts() = error("not used")
    }

    private class NoOpSyncRepository : SyncRepository {
        override fun observeStatus(): Flow<SyncStatus> = flowOf(SyncStatus.NotSignedIn)
        override fun observePendingCount(): Flow<Int> = flowOf(0)
        override fun syncNow() = Unit
    }

    private fun buildViewModel(accounts: ScriptedAccountRepository, configured: Boolean = true): SignInViewModel {
        val sync = NoOpSyncRepository()
        return SignInViewModel(
            requestCode = RequestSignInCode(accounts),
            confirmCode = ConfirmSignInCode(accounts, sync),
            time = clock,
            configured = configured,
        )
    }

    @Test
    fun `configured false renders the unavailable state and nothing else`() = runTest(dispatcher) {
        val vm = buildViewModel(ScriptedAccountRepository(), configured = false)

        assertFalse(vm.state.value.configured)
    }

    @Test
    fun `submitting an email moves through requesting to awaiting code`() = runTest(dispatcher) {
        val accounts = ScriptedAccountRepository()
        val vm = buildViewModel(accounts)

        vm.onEvent(SignInEvent.EmailChanged("user@example.test"))
        vm.onEvent(SignInEvent.SubmitEmail)
        advanceUntilIdle()

        assertTrue(vm.state.value.step is SignInStep.AwaitingCode)
        assertEquals("user@example.test", vm.state.value.step.email)
        assertEquals("user@example.test", accounts.lastRequestedEmail)
    }

    @Test
    fun `resend is inert before resendAvailableAt and states when it becomes available`() = runTest(dispatcher) {
        val accounts = ScriptedAccountRepository().apply {
            nextCodeRequest = CodeRequest.Sent(clock.now().plus(Duration.ofSeconds(30)))
        }
        val vm = buildViewModel(accounts)
        vm.onEvent(SignInEvent.EmailChanged("user@example.test"))
        vm.onEvent(SignInEvent.SubmitEmail)
        // runCurrent(), not advanceUntilIdle(): the latter would also fast-forward
        // past the scheduled resend-enable delay below, defeating this test.
        runCurrent()

        assertFalse(vm.state.value.resendEnabled)
        assertEquals(1, accounts.requestCodeCallCount)

        // Inert before resendAvailableAt: the button does nothing. The gate reads
        // the injected clock, which has not moved yet.
        vm.onEvent(SignInEvent.ResendCode)
        runCurrent()
        assertEquals(1, accounts.requestCodeCallCount)

        // Once the wait has elapsed on both the injected clock (the gate) and the
        // coroutine scheduler (the delay backing resendEnabled), resend states it
        // is available and works.
        clock.advanceBy(Duration.ofSeconds(31))
        advanceTimeBy(Duration.ofSeconds(31).toMillis())
        runCurrent()
        assertTrue(vm.state.value.resendEnabled)

        vm.onEvent(SignInEvent.ResendCode)
        advanceUntilIdle()
        assertEquals(2, accounts.requestCodeCallCount)
    }

    @Test
    fun `an expired code returns to AwaitingCode with the email intact`() = runTest(dispatcher) {
        val accounts = ScriptedAccountRepository().apply {
            nextCodeConfirmation = CodeConfirmation.NotAccepted(CodeRejection.EXPIRED)
        }
        val vm = buildViewModel(accounts)
        vm.onEvent(SignInEvent.EmailChanged("user@example.test"))
        vm.onEvent(SignInEvent.SubmitEmail)
        advanceUntilIdle()

        vm.onEvent(SignInEvent.CodeChanged("000000"))
        vm.onEvent(SignInEvent.SubmitCode)
        advanceUntilIdle()

        assertTrue(vm.state.value.step is SignInStep.CodeNotAccepted)
        assertEquals("user@example.test", vm.state.value.step.email)
    }

    @Test
    fun `an incorrect code returns to AwaitingCode with the email intact`() = runTest(dispatcher) {
        val accounts = ScriptedAccountRepository().apply {
            nextCodeConfirmation = CodeConfirmation.NotAccepted(CodeRejection.INCORRECT)
        }
        val vm = buildViewModel(accounts)
        vm.onEvent(SignInEvent.EmailChanged("user@example.test"))
        vm.onEvent(SignInEvent.SubmitEmail)
        advanceUntilIdle()

        vm.onEvent(SignInEvent.CodeChanged("000000"))
        vm.onEvent(SignInEvent.SubmitCode)
        advanceUntilIdle()

        assertTrue(vm.state.value.step is SignInStep.CodeNotAccepted)
        assertEquals("user@example.test", vm.state.value.step.email)
    }

    @Test
    fun `offline gives NeedsConnection and never clears the email`() = runTest(dispatcher) {
        val accounts = ScriptedAccountRepository().apply {
            nextCodeRequest = CodeRequest.NeedsConnection
        }
        val vm = buildViewModel(accounts)
        vm.onEvent(SignInEvent.EmailChanged("user@example.test"))
        vm.onEvent(SignInEvent.SubmitEmail)
        advanceUntilIdle()

        assertTrue(vm.state.value.step is SignInStep.NeedsConnection)
        assertEquals("user@example.test", vm.state.value.step.email)
    }
}
