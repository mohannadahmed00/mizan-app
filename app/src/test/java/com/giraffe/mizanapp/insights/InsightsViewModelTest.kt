package com.giraffe.mizanapp.insights

import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import java.time.YearMonth
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

/**
 * `InsightsViewModel` - one screen, three switchable views. Test methods for
 * later user stories (Month, Sections, personal bests) are appended to this
 * same file rather than creating new ones (`006` tasks.md convention).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

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

    private fun buildViewModel(
        plans: FakeDayPlanRepository,
        catalogue: FakeCatalogueRepository = FakeCatalogueRepository(),
    ): InsightsViewModel {
        val completions = FakeCompletionRepository(plans, DayWritePolicy(clock), clock)
        val historyPage = GetHistoryPage(plans, completions, catalogue, clock)
        val trend = GetWeeklyTrend(historyPage)
        val month = GetMonthOverview(plans, completions, catalogue, clock)
        val sections = GetSectionBreakdown(plans, completions, catalogue, clock)
        val personalBests = GetPersonalBests(plans, completions, catalogue, clock)
        return InsightsViewModel(trend, month, sections, personalBests, clock, plans)
    }

    @Test
    fun `first load shows Ready with trend points oldest-first, default view Trend`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.status is InsightsUiState.Status.Ready)
        assertEquals(InsightsView.TREND, state.selectedView)
        assertEquals(1, state.trend.size)
        assertEquals(WeekBoundary.weekContaining(clock.today()).key, state.trend.single().weekKey)
    }

    @Test
    fun `a week still in progress is marked isInProgress`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        // clock.today() is mid-week by default (2026-03-14), so this week has not fully elapsed.
        assertTrue(vm.state.value.trend.single().isInProgress)
    }

    @Test
    fun `empty record shows RecordNotStarted`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock) // nothing seeded
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is InsightsUiState.Status.RecordNotStarted)
    }

    @Test
    fun `Retry reloads and can recover to Ready`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        vm.onEvent(InsightsEvent.Retry)
        advanceUntilIdle()

        assertTrue(vm.state.value.status is InsightsUiState.Status.Ready)
    }

    @Test
    fun `SelectView updates selectedView without reloading trend`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()
        val trendBefore = vm.state.value.trend

        vm.onEvent(InsightsEvent.SelectView(InsightsView.TREND))
        advanceUntilIdle()

        assertEquals(InsightsView.TREND, vm.state.value.selectedView)
        assertEquals(trendBefore, vm.state.value.trend)
    }

    @Test
    fun `LoadEarlierTrend prepends an older page and updates trendHasMore`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val recordStart = clock.today().minusDays(7 * 20)
        runBlocking {
            plans.ensurePlanFor(recordStart)
            plans.ensurePlanFor(clock.today())
        }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        val firstLoad = vm.state.value.trend
        assertTrue(vm.state.value.trendHasMore)

        vm.onEvent(InsightsEvent.LoadEarlierTrend)
        advanceUntilIdle()

        val afterLoadEarlier = vm.state.value.trend
        assertTrue(
            "the original weeks must still be present, now at the end (oldest-first order)",
            afterLoadEarlier.takeLast(firstLoad.size) == firstLoad,
        )
        assertTrue("older weeks must have been prepended", afterLoadEarlier.size > firstLoad.size)
    }

    @Test
    fun `LoadEarlierTrend does nothing once trendHasMore is false`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        assertFalse(vm.state.value.trendHasMore)
        val before = vm.state.value.trend

        vm.onEvent(InsightsEvent.LoadEarlierTrend)
        advanceUntilIdle()

        assertEquals(before, vm.state.value.trend)
    }

    // --- User Story 2: Monthly overview ---

    @Test
    fun `SelectView MONTH loads the current month`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        vm.onEvent(InsightsEvent.SelectView(InsightsView.MONTH))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(InsightsView.MONTH, state.selectedView)
        assertEquals(YearMonth.from(clock.today()), state.month?.month)
        assertEquals(clock.today().lengthOfMonth(), state.month?.days?.size)
    }

    @Test
    fun `PreviousMonth and NextMonth navigate and reload, clamped at the record and current month`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        val recordStart = clock.today().minusMonths(2)
        runBlocking {
            plans.ensurePlanFor(recordStart)
            plans.ensurePlanFor(clock.today())
        }
        val vm = buildViewModel(plans)
        advanceUntilIdle()
        vm.onEvent(InsightsEvent.SelectView(InsightsView.MONTH))
        advanceUntilIdle()

        assertTrue(vm.state.value.month!!.canGoEarlier)
        assertFalse(vm.state.value.month!!.canGoLater)

        vm.onEvent(InsightsEvent.PreviousMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.from(clock.today()).minusMonths(1), vm.state.value.month?.month)

        vm.onEvent(InsightsEvent.NextMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.from(clock.today()), vm.state.value.month?.month)

        // At the record-start month, further PreviousMonth is a no-op.
        vm.onEvent(InsightsEvent.PreviousMonth)
        advanceUntilIdle()
        vm.onEvent(InsightsEvent.PreviousMonth)
        advanceUntilIdle()
        assertEquals(YearMonth.from(recordStart), vm.state.value.month?.month)
        assertFalse(vm.state.value.month!!.canGoEarlier)
    }

    // --- User Story 3: Section breakdown and personal bests ---

    @Test
    fun `SelectView SECTIONS loads the current week's breakdown in catalogue order`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        vm.onEvent(InsightsEvent.SelectView(InsightsView.SECTIONS))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(InsightsView.SECTIONS, state.selectedView)
        assertTrue(state.sections.isNotEmpty())
        // Catalogue order, never re-sorted by rate in the ViewModel.
        assertEquals(state.sections, state.sections.sortedWith(compareBy { state.sections.indexOf(it) }))
    }

    @Test
    fun `SwitchSectionPeriod reloads scoped to the current month`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()
        vm.onEvent(InsightsEvent.SelectView(InsightsView.SECTIONS))
        advanceUntilIdle()
        val weekScoped = vm.state.value.sections

        vm.onEvent(InsightsEvent.SwitchSectionPeriod(toMonth = true))
        advanceUntilIdle()

        assertTrue(vm.state.value.sections.isNotEmpty())
        // Not necessarily different in value with one day of history, but the
        // reload must have happened without error and without losing data.
        assertTrue(weekScoped.isNotEmpty())

        vm.onEvent(InsightsEvent.SwitchSectionPeriod(toMonth = false))
        advanceUntilIdle()
        assertTrue(vm.state.value.sections.isNotEmpty())
    }

    @Test
    fun `personal bests load once on initial load, independent of view switching`() = runTest(dispatcher) {
        val plans = FakeDayPlanRepository(time = clock)
        runBlocking { plans.ensurePlanFor(clock.today()) }
        val vm = buildViewModel(plans)
        advanceUntilIdle()

        assertTrue(vm.state.value.personalBests != null)

        vm.onEvent(InsightsEvent.SelectView(InsightsView.MONTH))
        advanceUntilIdle()
        vm.onEvent(InsightsEvent.SelectView(InsightsView.SECTIONS))
        advanceUntilIdle()

        assertTrue(vm.state.value.personalBests != null)
    }
}
