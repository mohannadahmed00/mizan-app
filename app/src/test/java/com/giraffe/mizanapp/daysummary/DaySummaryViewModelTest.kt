package com.giraffe.mizanapp.daysummary

import com.giraffe.mizanapp.domain.usecase.GetDaySummary
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DaySummaryViewModelTest {

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

    @Test
    fun `initial state is loading`() {
        val plans = FakeDayPlanRepository(time = clock)
        val vm = DaySummaryViewModel(GetDaySummary(plans, com.giraffe.mizanapp.today.FakeCompletionRepository(
            plans, com.giraffe.mizanapp.domain.policy.DayWritePolicy(clock), clock,
        )), clock.today())

        assertTrue(vm.state.value.status is DaySummaryUiState.Status.Loading)
    }

    @Test
    fun `a recorded date yields Ready with the correct figures`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val policy = com.giraffe.mizanapp.domain.policy.DayWritePolicy(clock)
        val completions = com.giraffe.mizanapp.today.FakeCompletionRepository(plans, policy, clock)
        val date = clock.today()
        kotlinx.coroutines.runBlocking {
            plans.ensurePlanFor(date)
            completions.record(date, "fajr-1")
        }

        val vm = DaySummaryViewModel(GetDaySummary(plans, completions), date)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Ready, got ${state.status}", state.status is DaySummaryUiState.Status.Ready)
        assertEquals(2, state.earnedPoints)
        assertEquals(69, state.availablePoints)
        assertEquals(date, state.civilDate)
    }

    @Test
    fun `a date with no plan yields NoRecord not an error`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val policy = com.giraffe.mizanapp.domain.policy.DayWritePolicy(clock)
        val completions = com.giraffe.mizanapp.today.FakeCompletionRepository(plans, policy, clock)

        val vm = DaySummaryViewModel(GetDaySummary(plans, completions), LocalDate.parse("2020-01-01"))
        advanceUntilIdle()

        assertTrue(vm.state.value.status is DaySummaryUiState.Status.NoRecord)
    }
}
