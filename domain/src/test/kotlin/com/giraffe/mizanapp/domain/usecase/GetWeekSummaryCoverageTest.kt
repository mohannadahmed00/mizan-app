package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backfill must never race ahead of what this device actually knows (FR-023b,
 * FR-023c): a date below the coverage floor is not yet known, and
 * materialising a competing local plan for it would create exactly the
 * collision FR-024a forbids.
 */
class GetWeekSummaryCoverageTest {

    private val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-16")) }
    private val week = WeekBoundary.weekContaining(time.today())

    @Test
    fun `with incomplete coverage ensurePlanFor is never called below knownFrom, and those dates are NOT_YET_KNOWN`() = runTest {
        val floor = week.dates[3]
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = floor.minusDays(30), origin = PlanOrigin.OPENED) { "id-1" })
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase(week)
        assertTrue(outcome is WeekOutcome.Ready)
        val summary = (outcome as WeekOutcome.Ready).summary

        // Restricted to elapsed dates: a future date is NOT_YET_ELAPSED
        // regardless of coverage (BuildDayCellsCoverageTest), so it is out of
        // scope for this assertion even when it also falls below the floor.
        val belowFloor = summary.days.filter { it.date.isBefore(floor) && !it.date.isAfter(time.today()) }
        assertTrue(belowFloor.isNotEmpty())
        assertTrue(belowFloor.all { it.state == DayCellState.NOT_YET_KNOWN })
        assertEquals(0, plans.creationCount)
    }

    @Test
    fun `with complete coverage the backfill behaviour is unchanged`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage.completeFrom(LocalDate.parse("2026-01-01")))
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = LocalDate.parse("2026-01-01"), origin = PlanOrigin.OPENED) { "id-1" })
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase(week)
        assertTrue(outcome is WeekOutcome.Ready)

        // Every elapsed date in the week gets backfilled, exactly as before coverage existed.
        // `it.isBefore(today)` matches GetWeekSummary's own backfill filter — today is excluded.
        assertEquals(week.dates.count { it.isBefore(time.today()) }, plans.creationCount)
    }
}
