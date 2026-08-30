package com.giraffe.mizanapp.today

import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.streak.ActivityState
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A completion repository whose consistency-dates read can be told to fail. */
private class FlakyCompletionRepository(
    private val base: CompletionRepository,
    var shouldThrow: Boolean = true,
) : CompletionRepository by base {
    override fun observeConsistencyDates(): Flow<List<LocalDate>> =
        if (shouldThrow) flow { throw RuntimeException("simulated read failure") } else base.observeConsistencyDates()
}

/**
 * The streak collector runs on [Dispatchers.Default] (see
 * `TodayViewModel.observeStreak`), deliberately off the virtual-time
 * dispatcher `advanceUntilIdle()` controls — `GetStreakSummary` reschedules
 * itself indefinitely to track the 20:00 and midnight boundaries, and
 * `advanceUntilIdle()` cannot drain a queue that never empties. That means
 * these tests cannot use `advanceUntilIdle()` to know the panel has updated;
 * they poll real wall-clock time instead. The work being waited on is a
 * synchronous in-memory map, so this settles in well under a millisecond in
 * practice — the bound below is generous headroom, not an expected duration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayStreakTest {

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

    private fun viewModel(
        catalogue: FakeCatalogueRepository = FakeCatalogueRepository(),
        completionsOverride: ((CompletionRepository) -> CompletionRepository)? = null,
    ): Triple<TodayViewModel, FakeDayPlanRepository, CompletionRepository> {
        val plans = FakeDayPlanRepository()
        val policy = DayWritePolicy(clock)
        val realCompletions = FakeCompletionRepository(plans, policy, clock)
        val completions = completionsOverride?.invoke(realCompletions) ?: realCompletions
        val getStreakSummary = GetStreakSummary(completions, plans, clock, FakeRecordCoverageRepository())
        val vm = TodayViewModel(
            catalogue = catalogue,
            dayPlans = plans,
            completions = completions,
            time = clock,
            getStreakSummary = getStreakSummary,
            boundaryStatus = FakeBoundaryStatus(),
        )
        return Triple(vm, plans, completions)
    }

    private fun awaitStreak(vm: TodayViewModel, timeoutMs: Long = 2_000): StreakPanelUi {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = vm.state.value.streak
        while (last is StreakPanelUi.Resolving && System.currentTimeMillis() < deadline) {
            Thread.sleep(2)
            last = vm.state.value.streak
        }
        return last
    }

    private fun awaitStreakWhere(
        vm: TodayViewModel,
        timeoutMs: Long = 2_000,
        predicate: (StreakPanelUi) -> Boolean,
    ): StreakPanelUi {
        val deadline = System.currentTimeMillis() + timeoutMs
        var current = vm.state.value.streak
        while (!predicate(current) && System.currentTimeMillis() < deadline) {
            Thread.sleep(2)
            current = vm.state.value.streak
        }
        return current
    }

    @Test
    fun `initial state is resolving`() {
        assertEquals(StreakPanelUi.Resolving, TodayUiState().streak)
    }

    @Test
    fun `resolves to figures after recording`() = runTest(dispatcher) {
        val (vm, _, _) = viewModel()
        advanceUntilIdle()

        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()

        val streak = awaitStreakWhere(vm) { it is StreakPanelUi.Ready && it.current == 1 }
        assertTrue(streak is StreakPanelUi.Ready)
        assertEquals(1, (streak as StreakPanelUi.Ready).current)
    }

    @Test
    fun `streak survives catalogue unavailable`() = runTest(dispatcher) {
        val failing = FakeCatalogueRepository(
            failWith = listOf(com.giraffe.mizanapp.domain.catalogue.CatalogueDefect.NoCatalogue("/catalogue/valid-catalogue.json")),
        )
        val (vm, _, _) = viewModel(failing)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is TodayUiState.Status.CatalogueUnavailable)
        val streak = awaitStreak(vm)
        assertTrue("the panel must still resolve", streak is StreakPanelUi.Ready)
    }

    @Test
    fun `read failure surfaces as unavailable without blocking the screen`() = runTest(dispatcher) {
        val (vm, _, _) = viewModel(completionsOverride = { FlakyCompletionRepository(it, shouldThrow = true) })
        advanceUntilIdle()

        val streak = awaitStreakWhere(vm) { it is StreakPanelUi.Unavailable }
        assertTrue(streak is StreakPanelUi.Unavailable)
        assertTrue("tasks remain usable", vm.state.value.status is TodayUiState.Status.Ready)
    }

    @Test
    fun `retry re-subscribes after the source recovers`() = runTest(dispatcher) {
        lateinit var flaky: FlakyCompletionRepository
        val (vm, _, _) = viewModel(completionsOverride = { base ->
            FlakyCompletionRepository(base, shouldThrow = true).also { flaky = it }
        })
        advanceUntilIdle()
        val unavailable = awaitStreakWhere(vm) { it is StreakPanelUi.Unavailable }
        assertTrue(unavailable is StreakPanelUi.Unavailable)

        flaky.shouldThrow = false
        vm.onEvent(TodayEvent.RetryStreak)
        advanceUntilIdle()

        val ready = awaitStreakWhere(vm) { it is StreakPanelUi.Ready }
        assertTrue(ready is StreakPanelUi.Ready)
    }

    @Test
    fun `undo returns the run to its previous figure without a reload`() = runTest(dispatcher) {
        val (vm, _, _) = viewModel()
        advanceUntilIdle()
        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()
        val counted = awaitStreakWhere(vm) { it is StreakPanelUi.Ready && it.current == 1 }
        assertEquals(1, (counted as StreakPanelUi.Ready).current)

        vm.onEvent(TodayEvent.UndoTask("fajr-1"))
        advanceUntilIdle()

        val reverted = awaitStreakWhere(vm) { it is StreakPanelUi.Ready && it.current == 0 }
        assertEquals(0, (reverted as StreakPanelUi.Ready).current)
    }

    @Test
    fun `recent activity has seven entries with today last after recording`() = runTest(dispatcher) {
        val (vm, _, _) = viewModel()
        advanceUntilIdle()
        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()

        val ready = awaitStreakWhere(vm) { it is StreakPanelUi.Ready && it.current == 1 } as StreakPanelUi.Ready
        assertEquals(7, ready.recentActivity.size)
        assertEquals(ActivityState.COUNTED, ready.recentActivity.last().state)
    }
}
