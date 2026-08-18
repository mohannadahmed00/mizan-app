package com.giraffe.mizanapp.profile

import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.LocalRecordCounts
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class ScriptedAccountRepository(
        session: AccountSession,
        private val counts: LocalRecordCounts,
    ) : AccountRepository {
        val sessionFlow = MutableStateFlow(session)
        var lastDisplayName: String? = "unset"
        var signOutCalls = mutableListOf<SignOutMode>()

        override fun observeSession(): Flow<AccountSession> = sessionFlow
        override suspend fun requestCode(email: String): CodeRequest = error("not used")
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation =
            error("not used")

        override suspend fun signOut(mode: SignOutMode) {
            signOutCalls += mode
        }

        override suspend fun updateDisplayName(name: String?) {
            lastDisplayName = name
        }

        override suspend fun localRecordCounts(): LocalRecordCounts = counts
    }

    private class ScriptedSyncRepository(pending: Int) : SyncRepository {
        val pendingFlow = MutableStateFlow(pending)
        override fun observeStatus(): Flow<SyncStatus> = MutableStateFlow(SyncStatus.UpToDate)
        override fun observePendingCount(): Flow<Int> = pendingFlow
        override fun syncNow() = Unit
    }

    private fun buildViewModel(
        session: AccountSession = AccountSession.SignedIn(userId = "u-1", email = "user@example.test"),
        counts: LocalRecordCounts = LocalRecordCounts(recordedDays = 12, completionCount = 40),
        pending: Int = 0,
    ): Triple<ProfileViewModel, ScriptedAccountRepository, ScriptedSyncRepository> {
        val accounts = ScriptedAccountRepository(session, counts)
        val sync = ScriptedSyncRepository(pending)
        val viewModel = ProfileViewModel(accounts, sync, SignOut(accounts, sync), UpdateDisplayName(accounts))
        return Triple(viewModel, accounts, sync)
    }

    @Test
    fun display_name_saves() = runTest {
        val (viewModel, accounts, _) = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.DisplayNameChanged("Ahmed"))
        viewModel.onEvent(ProfileEvent.SaveDisplayName)
        advanceUntilIdle()

        assertEquals("Ahmed", viewModel.state.value.displayName)
        assertEquals("Ahmed", accounts.lastDisplayName)
    }

    @Test
    fun display_name_clears() = runTest {
        val (viewModel, accounts, _) = buildViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ProfileEvent.DisplayNameChanged("Ahmed"))
        viewModel.onEvent(ProfileEvent.SaveDisplayName)
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.ClearDisplayName)
        advanceUntilIdle()

        assertNull(viewModel.state.value.displayName)
        assertNull(accounts.lastDisplayName)
    }

    @Test
    fun a_cleared_display_name_falls_back_to_the_email() = runTest {
        val (viewModel, _, _) = buildViewModel(
            session = AccountSession.SignedIn(userId = "u-1", email = "fallback@example.test"),
        )
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.ClearDisplayName)
        advanceUntilIdle()

        assertNull(viewModel.state.value.displayName)
        assertEquals("fallback@example.test", viewModel.state.value.email)
    }

    @Test
    fun both_sign_out_paths_surface_a_confirmation_first() = runTest {
        val (viewModel, accounts, _) = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.SignOut)
        assertTrue(viewModel.state.value.confirming is SignOutConfirmation.Plain)
        assertTrue("no signOut() call before ConfirmSignOut", accounts.signOutCalls.isEmpty())

        viewModel.onEvent(ProfileEvent.CancelSignOut)
        assertNull(viewModel.state.value.confirming)

        viewModel.onEvent(ProfileEvent.SignOutAndRemoveData)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.confirming is SignOutConfirmation.Removing)
        assertTrue("no signOut() call before ConfirmSignOut", accounts.signOutCalls.isEmpty())
    }

    @Test
    fun the_removing_confirmation_names_the_day_count_and_the_completion_count() = runTest {
        val (viewModel, _, _) = buildViewModel(counts = LocalRecordCounts(recordedDays = 21, completionCount = 87))
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.SignOutAndRemoveData)
        advanceUntilIdle()

        val confirming = viewModel.state.value.confirming
        assertTrue(confirming is SignOutConfirmation.Removing)
        confirming as SignOutConfirmation.Removing
        assertEquals(21, confirming.recordedDays)
        assertEquals(87, confirming.completions)
    }

    @Test
    fun both_confirmations_warn_when_the_pending_count_is_non_zero() = runTest {
        val (viewModel, _, _) = buildViewModel(pending = 9)
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.SignOut)
        assertEquals(9, (viewModel.state.value.confirming as SignOutConfirmation.Plain).pendingCount)
        viewModel.onEvent(ProfileEvent.CancelSignOut)

        viewModel.onEvent(ProfileEvent.SignOutAndRemoveData)
        advanceUntilIdle()
        assertEquals(9, (viewModel.state.value.confirming as SignOutConfirmation.Removing).pendingCount)
    }

    @Test
    fun confirming_a_sign_out_actually_calls_signOut_with_the_right_mode() = runTest {
        val (viewModel, accounts, _) = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.SignOutAndRemoveData)
        advanceUntilIdle()
        viewModel.onEvent(ProfileEvent.ConfirmSignOut)
        advanceUntilIdle()

        assertEquals(listOf(SignOutMode.REMOVE_LOCAL_RECORDS), accounts.signOutCalls)
        assertNull(viewModel.state.value.confirming)
    }

    @Test
    fun the_conflict_policy_statement_is_present_in_the_state() = runTest {
        val (viewModel, _, _) = buildViewModel()
        advanceUntilIdle()

        val policy = viewModel.state.value.conflictPolicy
        assertTrue(policy.isNotBlank())
        assertTrue(policy.contains("kept", ignoreCase = true))
        assertTrue(policy.contains("undone", ignoreCase = true))
    }

    @Test
    fun no_state_string_contains_a_forbidden_word() = runTest {
        val (viewModel, _, _) = buildViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ProfileEvent.SignOutAndRemoveData)
        advanceUntilIdle()

        val forbidden = listOf(
            "failed", "failure", "error", "lost", "missing", "problem", "wrong",
            "you didn't", "you haven't", "retry now",
        )
        val state = viewModel.state.value
        val strings = listOfNotNull(state.email, state.displayName, state.conflictPolicy, state.draftDisplayName)
        for (text in strings) {
            for (word in forbidden) {
                assertFalse("\"$text\" must not contain \"$word\"", text.contains(word, ignoreCase = true))
            }
        }
    }
}
