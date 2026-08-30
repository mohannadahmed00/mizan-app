package com.giraffe.mizanapp.today

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
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPromptStateTest {

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

    private fun viewModel(boundaryStatus: FakeBoundaryStatus): TodayViewModel {
        val plans = FakeDayPlanRepository()
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        return TodayViewModel(
            catalogue = FakeCatalogueRepository(),
            dayPlans = plans,
            completions = completions,
            time = clock,
            getStreakSummary = GetStreakSummary(completions, plans, clock, FakeRecordCoverageRepository(), boundaryStatus),
            boundaryStatus = boundaryStatus,
        )
    }

    @Test
    fun thePromptIsVisibleOnFirstLaunch() = runTest(dispatcher) {
        val vm = viewModel(FakeBoundaryStatus())
        advanceUntilIdle()

        assertTrue(vm.state.value.locationPrompt.visible)
    }

    @Test
    fun thePromptIsInvisibleOncePromptShownIsTrue() = runTest(dispatcher) {
        val boundaryStatus = FakeBoundaryStatus().apply { setPromptShown(true) }
        val vm = viewModel(boundaryStatus)
        advanceUntilIdle()

        assertFalse(vm.state.value.locationPrompt.visible)
    }

    @Test
    fun dismissLocationPromptHidesItAndChangesNothingElse() = runTest(dispatcher) {
        val vm = viewModel(FakeBoundaryStatus())
        advanceUntilIdle()
        val before = vm.state.value

        vm.onEvent(TodayEvent.DismissLocationPrompt)
        advanceUntilIdle()

        val after = vm.state.value
        assertFalse(after.locationPrompt.visible)
        assertEqualsIgnoringPrompt(before, after)
    }

    @Test
    fun theTodayStateIsFullyPopulatedWhileThePromptIsVisible() = runTest(dispatcher) {
        val vm = viewModel(FakeBoundaryStatus())
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.locationPrompt.visible)
        assertTrue(state.status is TodayUiState.Status.Ready)
        assertTrue(state.sections.isNotEmpty())
    }

    private fun assertEqualsIgnoringPrompt(before: TodayUiState, after: TodayUiState) {
        org.junit.Assert.assertEquals(before.copy(locationPrompt = after.locationPrompt), after)
    }
}
