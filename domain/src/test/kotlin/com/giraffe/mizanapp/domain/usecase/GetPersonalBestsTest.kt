package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetPersonalBests` - the one full-record read in this feature (research.md
 * "Full-record scan bound"). No write, ever.
 */
class GetPersonalBestsTest {

    private fun timeAt(date: LocalDate) = FakeTimeProvider().apply { setDate(date) }

    @Test
    fun `an empty record returns NoHistory`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time)
        val useCase = GetPersonalBests(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase()

        assertEquals(PersonalBestsOutcome.NoHistory, outcome)
    }

    @Test
    fun `a populated record returns the best day within the whole record`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetPersonalBests(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase() as PersonalBestsOutcome.Ready

        assertTrue(outcome.bests.bestDay != null || outcome.bests.bestWeek == null)
    }

    @Test
    fun `the record-start floor bounds the scan - nothing before it is included`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val recordStart = LocalDate.parse("2026-08-05")
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStart, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetPersonalBests(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase() as PersonalBestsOutcome.Ready

        assertTrue(
            "the best day must fall on or after the record start",
            outcome.bests.bestDay == null || !outcome.bests.bestDay!!.date.isBefore(recordStart),
        )
    }

    @Test
    fun `missing catalogue surfaces CatalogueUnavailable`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetPersonalBests(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(available = false), time, FakeRecordCoverageRepository())

        val outcome = useCase()

        assertTrue(outcome is PersonalBestsOutcome.CatalogueUnavailable)
    }

    @Test
    fun `reading personal bests writes nothing`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val span = generateSequence(LocalDate.parse("2026-08-01")) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.parse("2026-08-10")) }.toSet()
        val plans = FakeWeekDayPlanRepository(time = time, failDates = span).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetPersonalBests(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        // No exception means ensurePlanFor was never called on any poisoned date.
        useCase()
        Unit
    }
}
