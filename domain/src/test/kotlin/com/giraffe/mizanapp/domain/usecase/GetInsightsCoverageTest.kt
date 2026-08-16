package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetMonthOverview`, `GetSectionBreakdown` and `GetPersonalBests` all read
 * over a date range that can straddle the coverage floor: unfetched dates
 * must read `NOT_YET_KNOWN`, never a fabricated 0%, and the whole result is
 * marked provisional until coverage over that range completes (FR-023d).
 */
class GetInsightsCoverageTest {

    private val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-16")) }
    private val recordStart = LocalDate.parse("2026-01-01")
    private val floor = LocalDate.parse("2026-08-01")

    private fun seededPlans(): FakeWeekDayPlanRepository {
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = recordStart, origin = PlanOrigin.OPENED) { "id-1" })
        return plans
    }

    @Test
    fun `GetMonthOverview marks unfetched dates NOT_YET_KNOWN and the result provisional`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val useCase = GetMonthOverview(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase(YearMonth.of(2026, 8))
        assertTrue(outcome is MonthOverviewOutcome.Ready)
        val overview = (outcome as MonthOverviewOutcome.Ready).overview

        assertTrue(overview.days.filter { it.date.isBefore(floor) }.all { it.state == DayCellState.NOT_YET_KNOWN })
        assertTrue("result must be marked provisional while coverage is incomplete", overview.provisional)
    }

    @Test
    fun `GetSectionBreakdown marks the result provisional while coverage over the period is incomplete`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val useCase = GetSectionBreakdown(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase(InsightsPeriod.ForWeek(WeekBoundary.weekContaining(time.today())))
        assertTrue(outcome is SectionBreakdownOutcome.Ready)
        assertTrue((outcome as SectionBreakdownOutcome.Ready).provisional)
    }

    @Test
    fun `GetPersonalBests marks the result provisional while coverage over the full record is incomplete`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val useCase = GetPersonalBests(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase()
        assertTrue(outcome is PersonalBestsOutcome.Ready)
        assertTrue((outcome as PersonalBestsOutcome.Ready).bests.provisional)
    }

    @Test
    fun `all three are not provisional once coverage is complete`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage.completeFrom(recordStart))

        val month = GetMonthOverview(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)(YearMonth.of(2026, 8))
        assertFalse((month as MonthOverviewOutcome.Ready).overview.provisional)

        val section = GetSectionBreakdown(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)(
            InsightsPeriod.ForWeek(WeekBoundary.weekContaining(time.today())),
        )
        assertFalse((section as SectionBreakdownOutcome.Ready).provisional)

        val bests = GetPersonalBests(seededPlans(), FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)()
        assertFalse((bests as PersonalBestsOutcome.Ready).bests.provisional)
    }
}
