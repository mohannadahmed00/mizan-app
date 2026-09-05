package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SC-006: the notification path (no backfill) and the weekly sheet (which
 * backfills) must never disagree about a closed week's figures. If this
 * fails, the fix belongs in [GetClosedWeekSummary]'s projection — the sheet
 * is the reference implementation here, never the thing to change.
 */
class SummaryAgreesWithSheetTest {

    private val week = WeekBoundary.weekContaining(DayFixtures.dateFor(DayOfWeek.SATURDAY))
    private val catalogue = DayFixtures.catalogue

    @Test fun bothPathsReportTheSameFigures() = runTest {
        // Open five of the seven days directly (mixed activity); leave two never-opened.
        val sheetTime = FakeTimeProvider().apply { setDate(week.end.plusDays(1)) } // the week has fully elapsed
        val sheetPlans = FakeWeekDayPlanRepository(catalogue = catalogue, time = sheetTime)
        val sheetCompletions = FakeWeekCompletionRepository()
        week.dates.take(5).forEach { date ->
            val created = sheetPlans.ensurePlanFor(date)
            val plan = (created as com.giraffe.mizanapp.domain.repository.EnsureOutcome.Created).plan
            if (date == week.dates[0]) {
                val task = plan.plannedTasks.first()
                sheetCompletions.seed(DayFixtures.completion(plan, task, index = 0))
            }
        }

        val sheetCatalogue = FakeWeekCatalogueRepository(catalogue)
        val sheetCoverage = FakeRecordCoverageRepository()
        val sheetOutcome = GetWeekSummary(sheetPlans, sheetCompletions, sheetCatalogue, sheetTime, sheetCoverage).invoke(week) as WeekOutcome.Ready

        // Same seed, but read through the closed-week path, which must not backfill the two
        // never-opened days and must instead project them.
        val closedPlans = FakeWeekDayPlanRepository(catalogue = catalogue, time = sheetTime)
        week.dates.take(5).forEach { closedPlans.seedPlan(sheetPlans.planFor(it)!!) }
        val closedCompletions = FakeWeekCompletionRepository()
        sheetCompletions.liveBetween(week.start, week.end).forEach { closedCompletions.seed(it) }
        val closedCatalogue = FakeWeekCatalogueRepository(catalogue)
        val closedCoverage = FakeRecordCoverageRepository()
        val closedOutcome = GetClosedWeekSummary(closedPlans, closedCompletions, closedCatalogue, closedCoverage).invoke(week) as ClosedWeekOutcome.Ready

        assertEquals(sheetOutcome.summary, closedOutcome.summary)
    }
}
