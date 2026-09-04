package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.SeedOutcome
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.buildWeekSummary
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two catalogue versions, selectable per date, so a projection can be pinned to the version
 *  that was actually effective rather than whichever is current (FR-024). */
private class TwoVersionCatalogueRepository(
    var v1: Catalogue,
    var v2: Catalogue,
    private val cutover: LocalDate,
) : CatalogueRepository {
    override suspend fun seedIfNeeded(): SeedOutcome = SeedOutcome.Seeded(2, v2.tasks.size)
    override suspend fun currentVersion(): Int? = 2
    override suspend fun versionEffectiveOn(date: LocalDate): Int? = if (date.isBefore(cutover)) 1 else 2
    override suspend fun catalogueAt(version: Int): Catalogue? = if (version == 1) v1 else v2
}

class GetClosedWeekSummaryTest {

    private val week = WeekBoundary.weekContaining(DayFixtures.dateFor(DayOfWeek.SATURDAY))
    private val v1Catalogue = DayFixtures.catalogue
    private val v2Catalogue = v1Catalogue.copy(taskVersions = v1Catalogue.taskVersions.map { it.copy(catalogueVersion = 2, points = it.points * 5) })

    private fun sut(
        plans: FakeWeekDayPlanRepository,
        completions: FakeWeekCompletionRepository = FakeWeekCompletionRepository(),
        catalogue: CatalogueRepository = TwoVersionCatalogueRepository(v1Catalogue, v2Catalogue, week.end.plusDays(1)),
        coverage: FakeRecordCoverageRepository = FakeRecordCoverageRepository(),
    ) = GetClosedWeekSummary(plans, completions, catalogue, coverage)

    private suspend fun fullyOpenedPlans(): FakeWeekDayPlanRepository {
        val plans = FakeWeekDayPlanRepository(catalogue = v1Catalogue, time = com.giraffe.mizanapp.domain.time.FakeTimeProvider().apply { setDate(week.start) })
        week.dates.forEach { plans.ensurePlanFor(it) }
        return plans
    }

    @Test fun weekWithStoredPlansForEveryDayMatchesBuildWeekSummaryDirectly() = runTest {
        val plans = fullyOpenedPlans()
        val completions = FakeWeekCompletionRepository()
        val outcome = sut(plans, completions).invoke(week) as ClosedWeekOutcome.Ready

        val storedPlans = plans.plansBetween(week.start, week.end)
        val expected = buildWeekSummary(
            week = week,
            today = week.end,
            recordStart = plans.earliestPlanDate(),
            plans = storedPlans,
            completions = emptyList(),
            projectedAvailable = emptyMap(),
        )
        assertEquals(expected, outcome.summary)
    }

    @Test fun twoUnopenedElapsedDaysProjectUsingVersionEffectiveOnDate() = runTest {
        val plans = FakeWeekDayPlanRepository(catalogue = v1Catalogue, time = com.giraffe.mizanapp.domain.time.FakeTimeProvider().apply { setDate(week.start) })
        // Open five of the seven days; leave two unopened.
        week.dates.take(5).forEach { plans.ensurePlanFor(it) }
        val missingDates = week.dates.drop(5)
        val catalogue = TwoVersionCatalogueRepository(v1Catalogue, v2Catalogue, week.end.plusDays(1)) // cutover after the week -> every date in-week resolves to v1
        val outcome = sut(plans, catalogue = catalogue).invoke(week) as ClosedWeekOutcome.Ready

        missingDates.forEach { date ->
            val cell = outcome.summary.days.first { it.date == date }
            val expectedAvailable = com.giraffe.mizanapp.domain.week.projectAvailablePoints(v1Catalogue, 1, date)
            assertEquals("date=$date should use v1 (versionEffectiveOn), not the current version", expectedAvailable, cell.available)
        }
    }

    @Test fun changingTheCatalogueAfterBuildingDoesNotAlterAnAlreadyFullyStoredWeek() = runTest {
        val plans = fullyOpenedPlans()
        val catalogue = TwoVersionCatalogueRepository(v1Catalogue, v2Catalogue, week.end.plusDays(1))
        val useCase = sut(plans, catalogue = catalogue)
        val first = (useCase.invoke(week) as ClosedWeekOutcome.Ready).summary

        // Simulate a catalogue change: the "current" content moves, but every date in this week
        // already has a stored plan, so nothing here may read the catalogue for it.
        catalogue.v1 = catalogue.v1.copy(taskVersions = catalogue.v1.taskVersions.map { it.copy(points = it.points * 100) })

        val second = (useCase.invoke(week) as ClosedWeekOutcome.Ready).summary
        assertEquals(first, second)
    }

    @Test fun weekWithNoCompletionsReturnsZeroEarnedAndNonZeroAvailable() = runTest {
        val plans = fullyOpenedPlans()
        val outcome = sut(plans).invoke(week) as ClosedWeekOutcome.Ready
        assertEquals(0, outcome.summary.score.earned)
        assertTrue(outcome.summary.score.elapsedAvailable > 0)
    }

    @Test fun weekOnlyPartlyInsideRecordedHistoryMarksTheDatesBeforeRecordStartAsOutsideRecord() = runTest {
        // recordStart falls inside the week: everything before it never had a plan and never will.
        val recordStart = week.dates[3]
        val plans = FakeWeekDayPlanRepository(catalogue = v1Catalogue, time = com.giraffe.mizanapp.domain.time.FakeTimeProvider().apply { setDate(week.start) })
        week.dates.drop(3).forEach { plans.ensurePlanFor(it) }
        val outcome = sut(plans).invoke(week) as ClosedWeekOutcome.Ready

        week.dates.take(3).forEach { date ->
            assertEquals(DayCellState.OUTSIDE_RECORD, outcome.summary.days.first { it.date == date }.state)
        }
    }
}
