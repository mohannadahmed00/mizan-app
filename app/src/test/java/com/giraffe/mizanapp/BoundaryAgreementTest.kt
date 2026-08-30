package com.giraffe.mizanapp

import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.history.HistoryViewModel
import com.giraffe.mizanapp.insights.InsightsEvent
import com.giraffe.mizanapp.insights.InsightsView
import com.giraffe.mizanapp.insights.InsightsViewModel
import com.giraffe.mizanapp.today.FakeBoundaryStatus
import com.giraffe.mizanapp.today.FakeCatalogueRepository
import com.giraffe.mizanapp.today.FakeClock
import com.giraffe.mizanapp.today.FakeCompletionRepository
import com.giraffe.mizanapp.today.FakeDayPlanRepository
import com.giraffe.mizanapp.today.FakeRecordCoverageRepository
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.week.WeekViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * US5, FR-010: between Maghrib and midnight there is a window where a fixed-midnight day and a
 * Maghrib day would disagree about the date. Every screen must read the same one, through
 * `TimeProvider.today()` and nowhere else -- research R2 predicts no production change is needed
 * here, because every consumer already does this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoundaryAgreementTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun everyScreenAgreesBetweenMaghribAndMidnight() = runTest(dispatcher) {
        // 23:30 Cairo -- after a Maghrib boundary already crossed today, before local midnight.
        val clock = FakeClock(instant = Instant.parse("2026-03-14T21:30:00Z"), zone = ZoneId.of("Africa/Cairo"))
        val today = clock.today()
        val expectedWeekKey = WeekBoundary.weekContaining(today).key

        val catalogue = FakeCatalogueRepository()
        val plans = FakeDayPlanRepository(time = clock)
        val policy = DayWritePolicy(clock)
        val completions = FakeCompletionRepository(plans, policy, clock)
        val coverage = FakeRecordCoverageRepository()
        val boundaryStatus = FakeBoundaryStatus(resolvedDate = today)

        plans.ensurePlanFor(today)

        val todayViewModel = TodayViewModel(
            catalogue = catalogue,
            dayPlans = plans,
            completions = completions,
            time = clock,
            getStreakSummary = GetStreakSummary(completions, plans, clock, coverage, boundaryStatus),
            boundaryStatus = boundaryStatus,
        )

        val weekViewModel = WeekViewModel(
            getWeekSummary = GetWeekSummary(plans, completions, catalogue, clock, coverage),
            catalogue = catalogue,
            time = clock,
            dayPlans = plans,
        )

        val historyPage = GetHistoryPage(plans, completions, catalogue, clock, coverage)
        val historyViewModel = HistoryViewModel(historyPage, catalogue)

        val insightsViewModel = InsightsViewModel(
            getWeeklyTrend = GetWeeklyTrend(historyPage),
            getMonthOverview = GetMonthOverview(plans, completions, catalogue, clock, coverage),
            getSectionBreakdown = GetSectionBreakdown(plans, completions, catalogue, clock, coverage),
            getPersonalBests = GetPersonalBests(plans, completions, catalogue, clock, coverage),
            time = clock,
            dayPlans = plans,
        )
        advanceUntilIdle()
        insightsViewModel.onEvent(InsightsEvent.SelectView(InsightsView.MONTH))
        advanceUntilIdle()

        assertEquals(today, todayViewModel.state.value.civilDate)
        assertEquals(expectedWeekKey, weekViewModel.state.value.weekKey)

        val historyWeekKey = historyViewModel.state.value.weeks.firstOrNull()?.weekKey
        assertEquals(expectedWeekKey, historyWeekKey)

        val insightsMonth = insightsViewModel.state.value.month?.month
        assertEquals(YearMonth.from(today), insightsMonth)
    }
}
