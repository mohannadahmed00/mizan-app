package com.giraffe.mizanapp.week

import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
class WeekNavigationTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var clock: FakeClock
    private lateinit var plans: FakeDayPlanRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The record starts on the Saturday two weeks before "today".
        clock = FakeClock().apply { setDate(LocalDate.parse("2026-08-22")) } // a Saturday
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * `ensurePlanFor` only creates an `OPENED` plan on "today", so to seed a
     * genuine record start elsewhere the clock moves there first and is
     * restored afterward.
     */
    private fun seededViewModel(recordStart: LocalDate): WeekViewModel {
        plans = FakeDayPlanRepository(time = clock)
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        val catalogue = FakeCatalogueRepository()
        val originalToday = clock.today()
        clock.setDate(recordStart)
        runBlocking { plans.ensurePlanFor(recordStart) }
        clock.setDate(originalToday)
        return WeekViewModel(GetWeekSummary(plans, completions, catalogue, clock), catalogue, clock, plans)
    }

    @Test
    fun `previous week moves to the preceding Saturday-to-Friday week`() = runTest(dispatcher) {
        val vm = seededViewModel(recordStart = LocalDate.parse("2026-08-08"))
        advanceUntilIdle()
        val currentStart = vm.state.value.startDate

        vm.onEvent(WeekEvent.PreviousWeek)
        advanceUntilIdle()

        assertEquals(currentStart!!.minusDays(7), vm.state.value.startDate)
    }

    @Test
    fun `at the week containing the record start, previous is unavailable and does nothing`() = runTest(dispatcher) {
        // Record start is inside the CURRENT week, so there is no earlier week to visit.
        val vm = seededViewModel(recordStart = clock.today())
        advanceUntilIdle()

        assertFalse(vm.state.value.canGoPrevious)
        val before = vm.state.value.startDate

        vm.onEvent(WeekEvent.PreviousWeek)
        advanceUntilIdle()

        assertEquals(before, vm.state.value.startDate)
    }

    @Test
    fun `at the current week, next is unavailable and does nothing`() = runTest(dispatcher) {
        val vm = seededViewModel(recordStart = LocalDate.parse("2026-08-08"))
        advanceUntilIdle()

        assertFalse(vm.state.value.canGoNext)
        val before = vm.state.value.startDate

        vm.onEvent(WeekEvent.NextWeek)
        advanceUntilIdle()

        assertEquals(before, vm.state.value.startDate)
    }

    @Test
    fun `showing the same week twice yields identical figures`() = runTest(dispatcher) {
        val vm = seededViewModel(recordStart = LocalDate.parse("2026-08-08"))
        advanceUntilIdle()

        vm.onEvent(WeekEvent.PreviousWeek)
        advanceUntilIdle()
        val once = vm.state.value

        vm.onEvent(WeekEvent.NextWeek)
        advanceUntilIdle()
        vm.onEvent(WeekEvent.PreviousWeek)
        advanceUntilIdle()
        val again = vm.state.value

        assertEquals(once.earnedPoints, again.earnedPoints)
        assertEquals(once.elapsedAvailablePoints, again.elapsedAvailablePoints)
        assertEquals(once.weekTargetPoints, again.weekTargetPoints)
    }

    @Test
    fun `a fresh ViewModel always opens on the current week`() = runTest(dispatcher) {
        val first = seededViewModel(recordStart = LocalDate.parse("2026-08-08"))
        advanceUntilIdle()
        first.onEvent(WeekEvent.PreviousWeek)
        advanceUntilIdle()
        assertTrue("sanity: navigation actually moved", first.state.value.startDate != WeekBoundary.weekContaining(clock.today()).start)

        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        val catalogue = FakeCatalogueRepository()
        val second = WeekViewModel(GetWeekSummary(plans, completions, catalogue, clock), catalogue, clock, plans)
        advanceUntilIdle()

        assertEquals(WeekBoundary.weekContaining(clock.today()).start, second.state.value.startDate)
    }

    @Test
    fun `crossing local midnight into a new week moves the sheet forward on refresh`() = runTest(dispatcher) {
        val vm = seededViewModel(recordStart = LocalDate.parse("2026-08-08"))
        advanceUntilIdle()
        val weekBefore = vm.state.value.startDate

        clock.setDate(LocalDate.parse("2026-08-29")) // the next Saturday: a new week
        vm.refreshForCurrentDate()
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-08-29"), vm.state.value.startDate)
        assertTrue(vm.state.value.startDate != weekBefore)
    }
}
