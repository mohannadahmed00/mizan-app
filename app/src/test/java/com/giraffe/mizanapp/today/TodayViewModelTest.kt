package com.giraffe.mizanapp.today

import com.giraffe.mizanapp.domain.catalogue.CatalogueDefect
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

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
    ): TodayViewModel {
        val plans = FakeDayPlanRepository()
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        return TodayViewModel(
            catalogue = catalogue,
            dayPlans = plans,
            completions = completions,
            time = clock,
            getStreakSummary = GetStreakSummary(completions, plans, clock, FakeRecordCoverageRepository()),
            boundaryStatus = FakeBoundaryStatus(),
        )
    }

    @Test
    fun `the screen becomes ready with today's plan`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.status is TodayUiState.Status.Ready)
        assertEquals(clock.today(), state.civilDate)
        assertEquals(69, state.availablePoints)
        assertEquals(0, state.earnedPoints)
        assertTrue("the hijri label is always present", !state.hijriLabel.isNullOrBlank())
    }

    @Test
    fun `completing a task raises the earned total by its points`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()

        assertEquals(2, vm.state.value.earnedPoints)
    }

    @Test
    fun `undoing lowers the earned total by the same points`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()

        vm.onEvent(TodayEvent.UndoTask("fajr-1"))
        advanceUntilIdle()

        assertEquals(0, vm.state.value.earnedPoints)
    }

    @Test
    fun `a multi-occurrence task shows progress toward its limit`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        repeat(3) { vm.onEvent(TodayEvent.CompleteTask("adhkar")); advanceUntilIdle() }

        val adhkar = vm.state.value.sections
            .flatMap { it.tasks }
            .first { it.slug == "adhkar" }
        assertEquals(3, adhkar.recordedCount)
        assertEquals(9, adhkar.maxOccurrences)
        assertTrue(adhkar.isMultiOccurrence)
        assertFalse(adhkar.isAtLimit)
        assertEquals(6, vm.state.value.earnedPoints)
    }

    @Test
    fun `recording past the limit changes nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(9) { vm.onEvent(TodayEvent.CompleteTask("adhkar")); advanceUntilIdle() }
        val earned = vm.state.value.earnedPoints

        vm.onEvent(TodayEvent.CompleteTask("adhkar"))
        advanceUntilIdle()

        assertEquals(earned, vm.state.value.earnedPoints)
        assertEquals(18, earned)
    }

    @Test
    fun `a failed seed surfaces as catalogue unavailable, not an empty day`() = runTest(dispatcher) {
        val failing = FakeCatalogueRepository(
            failWith = listOf(CatalogueDefect.NoCatalogue("/catalogue/valid-catalogue.json")),
        )

        val vm = viewModel(failing)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is TodayUiState.Status.CatalogueUnavailable)
        assertTrue(vm.state.value.sections.isEmpty())
    }

    @Test
    fun `the screen lands on the earliest incomplete section`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.currentSectionIndex)

        // Complete every task in the first section.
        val first = vm.state.value.sections.first()
        first.tasks.forEach { row ->
            repeat(row.maxOccurrences) { vm.onEvent(TodayEvent.CompleteTask(row.slug)); advanceUntilIdle() }
        }

        // Reload from scratch: the landing position is derived, never remembered.
        val fresh = viewModel()
        advanceUntilIdle()
        assertEquals(0, fresh.state.value.currentSectionIndex)
    }

    @Test
    fun `navigation clamps at both ends without erroring`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(TodayEvent.PreviousSection)
        assertEquals("must not go below zero", 0, vm.state.value.currentSectionIndex)

        repeat(vm.state.value.sections.size + 5) { vm.onEvent(TodayEvent.NextSection) }
        assertEquals(
            "must not go past the last section",
            vm.state.value.sections.lastIndex,
            vm.state.value.currentSectionIndex,
        )
        assertTrue(vm.state.value.status is TodayUiState.Status.Ready)
    }

    @Test
    fun `records made in one section survive moving to another`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(TodayEvent.CompleteTask("fajr-1"))
        advanceUntilIdle()

        vm.onEvent(TodayEvent.NextSection)
        vm.onEvent(TodayEvent.NextSection)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.earnedPoints)
    }

    @Test
    fun `crossing midnight moves the screen to the new date`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val firstDate = vm.state.value.civilDate

        clock.setDate(clock.today().plusDays(1))
        vm.refreshForCurrentDate()
        advanceUntilIdle()

        assertEquals(firstDate!!.plusDays(1), vm.state.value.civilDate)
        assertEquals("a new day starts empty", 0, vm.state.value.earnedPoints)
    }

    @Test
    fun `earned never exceeds available`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.state.value.sections.flatMap { it.tasks }.forEach { row ->
            repeat(row.maxOccurrences + 2) {
                vm.onEvent(TodayEvent.CompleteTask(row.slug)); advanceUntilIdle()
            }
        }

        val state = vm.state.value
        assertEquals(69, state.earnedPoints)
        assertEquals(69, state.availablePoints)
        assertEquals(1f, state.progressFraction, 0.0001f)
    }
}
