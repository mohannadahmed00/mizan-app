package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.domain.usecase.TrendOutcome
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-001/FR-009: opening or navigating Insights writes nothing. Later user
 * stories (Month, Sections, Personal Bests) append more test methods here
 * rather than creating new files (`006` tasks.md convention).
 */
@RunWith(AndroidJUnit4::class)
class InsightsNoWriteTest : DbTestBase() {

    @Test
    fun opening_and_scrolling_the_trend_changes_nothing_stored() = runTest {
        catalogue.seedIfNeeded()

        val recordStart = LocalDate.parse("2026-05-30")
        val currentWeekStart = LocalDate.parse("2026-08-15")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        time.setDate(currentWeekStart)
        dayPlans.ensurePlanFor(currentWeekStart)

        val plansBefore = db.dayPlanDao().countPlans()
        val completionsBefore = completions.liveBetween(LocalDate.parse("2000-01-01"), currentWeekStart).size

        val trend = GetWeeklyTrend(GetHistoryPage(dayPlans, completions, catalogue, time, coverageRepo))
        // Simulate opening Insights and scrolling the trend back several times.
        trend()
        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        repeat(3) {
            val outcome = trend(before = cursor)
            if (outcome is TrendOutcome.Ready) {
                cursor = outcome.weeks.firstOrNull()?.week?.key
                if (!outcome.hasMore) return@repeat
            }
        }

        val plansAfter = db.dayPlanDao().countPlans()
        val completionsAfter = completions.liveBetween(LocalDate.parse("2000-01-01"), currentWeekStart).size
        assertEquals("trend must create no plan rows", plansBefore, plansAfter)
        assertEquals("trend must create no completion rows", completionsBefore, completionsAfter)
    }

    @Test
    fun navigating_several_months_changes_nothing_stored() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-05-30")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        val currentDate = LocalDate.parse("2026-08-15")
        time.setDate(currentDate)
        dayPlans.ensurePlanFor(currentDate)

        val plansBefore = db.dayPlanDao().countPlans()

        val monthOverview = GetMonthOverview(dayPlans, completions, catalogue, time, coverageRepo)
        var month = YearMonth.from(currentDate)
        repeat(4) {
            monthOverview(month)
            month = month.minusMonths(1)
        }

        val plansAfter = db.dayPlanDao().countPlans()
        assertEquals("navigating months must create no plan rows", plansBefore, plansAfter)
    }

    @Test
    fun opening_sections_and_switching_scope_changes_nothing_stored() = runTest {
        catalogue.seedIfNeeded()
        val currentDate = LocalDate.parse("2026-08-15")
        time.setDate(currentDate)
        dayPlans.ensurePlanFor(currentDate)

        val plansBefore = db.dayPlanDao().countPlans()

        val sectionBreakdown = GetSectionBreakdown(dayPlans, completions, catalogue, time, coverageRepo)
        sectionBreakdown(InsightsPeriod.ForWeek(WeekBoundary.weekContaining(currentDate)))
        sectionBreakdown(InsightsPeriod.ForMonth(YearMonth.from(currentDate)))

        val plansAfter = db.dayPlanDao().countPlans()
        assertEquals("reading section breakdowns must create no plan rows", plansBefore, plansAfter)
    }

    @Test
    fun computing_personal_bests_changes_nothing_stored() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-05-30")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        val currentDate = LocalDate.parse("2026-08-15")
        time.setDate(currentDate)
        dayPlans.ensurePlanFor(currentDate)

        val plansBefore = db.dayPlanDao().countPlans()

        GetPersonalBests(dayPlans, completions, catalogue, time, coverageRepo)()

        val plansAfter = db.dayPlanDao().countPlans()
        assertEquals("computing personal bests must create no plan rows", plansBefore, plansAfter)
    }
}
