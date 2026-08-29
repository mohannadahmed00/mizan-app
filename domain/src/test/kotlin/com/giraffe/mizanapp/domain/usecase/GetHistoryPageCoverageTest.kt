package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A history page must never render an unfetched date as 0% or as absent
 * (FR-023b names history explicitly) — it is `NOT_YET_KNOWN`, and paging past
 * the coverage floor must not fabricate an empty week to fill the gap.
 */
class GetHistoryPageCoverageTest {

    private val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-16")) }

    @Test
    fun `a page covering dates below knownFrom reports them as NOT_YET_KNOWN, never 0 percent, never absent`() = runTest {
        val floor = LocalDate.parse("2026-08-10")
        val recordStart = LocalDate.parse("2026-01-01")
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = recordStart, origin = PlanOrigin.OPENED) { "id-1" })
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val outcome = useCase()
        assertTrue(outcome is HistoryOutcome.Ready)
        val page = (outcome as HistoryOutcome.Ready).page

        val belowFloorDays = page.weeks.flatMap { it.days }.filter { it.date.isBefore(floor) }
        assertTrue("expected at least one date below the coverage floor in the first page", belowFloorDays.isNotEmpty())
        assertTrue(belowFloorDays.all { it.state == DayCellState.NOT_YET_KNOWN })
        assertFalse(belowFloorDays.any { it.state == DayCellState.NOTHING_RECORDED })
    }

    @Test
    fun `paging past the coverage floor does not fabricate empty weeks`() = runTest {
        val floor = LocalDate.parse("2026-08-01")
        val recordStart = LocalDate.parse("2025-01-01")
        val coverage = FakeRecordCoverageRepository(RecordCoverage(knownFrom = floor, complete = false))
        val plans = FakeWeekDayPlanRepository(time = time)
        plans.seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = recordStart, origin = PlanOrigin.OPENED) { "id-1" })
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, coverage)

        val first = useCase()
        assertTrue(first is HistoryOutcome.Ready)
        val oldest = (first as HistoryOutcome.Ready).page.oldestLoaded
        assertTrue((first.page.hasMore))

        val second = useCase(before = oldest)
        assertTrue(second is HistoryOutcome.Ready)
        val secondPage = (second as HistoryOutcome.Ready).page

        // Every day in the second page still reports NOT_YET_KNOWN below the floor,
        // never a fabricated NOTHING_RECORDED week just because it is unfetched.
        val belowFloor = secondPage.weeks.flatMap { it.days }.filter { it.date.isBefore(floor) }
        assertTrue(belowFloor.isNotEmpty())
        assertTrue(belowFloor.all { it.state == DayCellState.NOT_YET_KNOWN })
    }
}
