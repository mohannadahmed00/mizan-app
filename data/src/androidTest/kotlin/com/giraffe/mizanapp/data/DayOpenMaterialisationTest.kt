package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import com.giraffe.mizanapp.domain.usecase.DayDetailOutcome
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.HistoryOutcome
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `005` FR-020, SC-008a, SC-009b, SC-011: opening a past day materialises
 * exactly one plan, marked backfilled, and never affects the streak.
 */
@RunWith(AndroidJUnit4::class)
class DayOpenMaterialisationTest : DbTestBase() {

    @Test
    fun opening_ten_unplanned_days_creates_exactly_ten_plans_and_no_completions() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-07-01")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)

        time.setDate(LocalDate.parse("2026-08-15"))
        val useCase = GetDayDetail(dayPlans, completions, catalogue, time)
        val plansBefore = db.dayPlanDao().countPlans()

        val tenDates = (1..10).map { recordStart.plusDays(it.toLong()) }
        tenDates.forEach { date -> useCase(date) }

        val plansAfter = db.dayPlanDao().countPlans()
        assertEquals(10, plansAfter - plansBefore)

        tenDates.forEach { date ->
            val plan = dayPlans.planFor(date)
            assertTrue("expected a stored plan for $date", plan != null)
            assertEquals(PlanOrigin.BACKFILLED, plan!!.origin)
        }
        assertEquals(0, completions.liveBetween(tenDates.first(), tenDates.last()).size)
    }

    @Test
    fun reopening_the_same_day_creates_nothing() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-07-01")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        time.setDate(LocalDate.parse("2026-08-15"))

        val useCase = GetDayDetail(dayPlans, completions, catalogue, time)
        val date = LocalDate.parse("2026-08-01")
        useCase(date)
        val plansAfterFirst = db.dayPlanDao().countPlans()

        useCase(date)
        val plansAfterSecond = db.dayPlanDao().countPlans()

        assertEquals(plansAfterFirst, plansAfterSecond)
    }

    @Test
    fun no_plan_is_created_for_a_date_before_the_record_start_or_after_today() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-08-08")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)

        val useCase = GetDayDetail(dayPlans, completions, catalogue, time)
        val plansBefore = db.dayPlanDao().countPlans()

        val beforeStart = useCase(recordStart.minusDays(5))
        val afterToday = useCase(time.today().plusDays(3))

        assertTrue(beforeStart is DayDetailOutcome.NoRecord)
        assertTrue(afterToday is DayDetailOutcome.NoRecord)
        assertEquals(plansBefore, db.dayPlanDao().countPlans())
        assertNull(dayPlans.planFor(recordStart.minusDays(5)))
        assertNull(dayPlans.planFor(time.today().plusDays(3)))
    }

    @Test
    fun streak_figures_are_unchanged_after_browsing_and_opening_days() = runTest {
        catalogue.seedIfNeeded()
        val recordStart = LocalDate.parse("2026-05-30")
        time.setDate(recordStart)
        dayPlans.ensurePlanFor(recordStart)
        completions.record(recordStart, "fajr-1")

        time.setDate(LocalDate.parse("2026-08-15"))
        dayPlans.ensurePlanFor(time.today())
        completions.record(time.today(), "fajr-1")

        val datesBefore = completions.observeConsistencyDates().first()
        val streakBefore = buildStreakSummary(datesBefore, time.today(), time.now(), time.zone(), dayPlans.earliestPlanDate())

        // Browse the whole record and open ten unplanned days.
        val historyUseCase = GetHistoryPage(dayPlans, completions, catalogue, time, coverageRepo)
        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        var hasMore: Boolean
        do {
            val outcome = historyUseCase(before = cursor) as HistoryOutcome.Ready
            cursor = outcome.page.oldestLoaded
            hasMore = outcome.page.hasMore
        } while (hasMore)

        val dayUseCase = GetDayDetail(dayPlans, completions, catalogue, time)
        (1..10).forEach { i -> dayUseCase(recordStart.plusDays(i.toLong())) }

        val datesAfter = completions.observeConsistencyDates().first()
        val streakAfter = buildStreakSummary(datesAfter, time.today(), time.now(), time.zone(), dayPlans.earliestPlanDate())

        assertEquals(streakBefore, streakAfter)
    }
}
