package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetDayDetail` - the six-step order in contracts/use-cases.md. Every test
 * here maps to one of the numbered guarantees.
 */
class GetDayDetailTest {

    private val date = DayFixtures.dateFor(DayOfWeek.SATURDAY)

    private fun timeAt(d: LocalDate) = FakeTimeProvider().apply { setDate(d) }

    @Test
    fun `a future date returns NoRecord without reading or writing`() = runBlocking {
        val time = timeAt(date)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(1), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(date.plusDays(1))

        assertTrue(outcome is DayDetailOutcome.NoRecord)
        assertEquals(0, plans.creationCount)
    }

    @Test
    fun `a date before the record start returns NoRecord without writing`() = runBlocking {
        val time = timeAt(date)
        val recordStart = date
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStart, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(recordStart.minusDays(1))

        assertTrue(outcome is DayDetailOutcome.NoRecord)
        assertEquals(0, plans.creationCount)
    }

    @Test
    fun `a date with a stored plan is summarised without writing`() = runBlocking {
        val time = timeAt(date)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(date)

        assertTrue("expected Ready, got $outcome", outcome is DayDetailOutcome.Ready)
        assertEquals(0, plans.creationCount)
    }

    @Test
    fun `an eligible unplanned date stores exactly one plan`() = runBlocking {
        val time = timeAt(date.plusDays(1)) // date has elapsed
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(10), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(date)

        assertTrue("expected Ready, got $outcome", outcome is DayDetailOutcome.Ready)
        assertEquals(1, plans.creationCount)
    }

    @Test
    fun `a failed store still returns Ready with derived figures`() = runBlocking {
        val time = timeAt(date.plusDays(1))
        val plans = FakeWeekDayPlanRepository(time = time, failDates = setOf(date)).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(10), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(date)

        assertTrue("expected Ready even though storage failed, got $outcome", outcome is DayDetailOutcome.Ready)
        val expected = buildDayPlan(DayFixtures.catalogue, 1, date, PlanOrigin.BACKFILLED) { "x" }.availablePoints
        assertEquals(expected, (outcome as DayDetailOutcome.Ready).summary.score.available)
        assertEquals(0, plans.creationCount) // the store never actually succeeded
    }

    @Test
    fun `derived and stored summaries are identical`() = runBlocking {
        val time = timeAt(date.plusDays(1))

        // Force the derive path: storage always fails for this date.
        val plansForDerived = FakeWeekDayPlanRepository(time = time, failDates = setOf(date)).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(10), PlanOrigin.OPENED) { "seed" })
        }
        val derivedOutcome = GetDayDetail(
            plansForDerived, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time,
        )(date) as DayDetailOutcome.Ready

        // A genuinely stored plan for the same date, read the ordinary way.
        val plansForStored = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(10), PlanOrigin.OPENED) { "seed" })
        }
        val storedOutcome = GetDayDetail(
            plansForStored, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time,
        )(date) as DayDetailOutcome.Ready

        assertEquals(storedOutcome.summary.score, derivedOutcome.summary.score)
        assertEquals(storedOutcome.summary.tasks.size, derivedOutcome.summary.tasks.size)
        assertEquals(
            storedOutcome.summary.tasks.map { it.task.taskSlug to it.task.points },
            derivedOutcome.summary.tasks.map { it.task.taskSlug to it.task.points },
        )
    }

    @Test
    fun `an unresolvable catalogue version returns CatalogueUnavailable, never an empty day`() = runBlocking {
        val time = timeAt(date.plusDays(1))
        // A catalogue repository that always fails to resolve a version - simulates
        // a storage failure that also prevents ensurePlanFor from succeeding.
        val brokenCatalogue = FakeWeekCatalogueRepository(available = false)
        val brokenPlans = FakeWeekDayPlanRepository(time = time, failDates = setOf(date)).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, date.minusDays(10), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetDayDetail(brokenPlans, FakeWeekCompletionRepository(), brokenCatalogue, time)

        val outcome = useCase(date)

        assertTrue("expected CatalogueUnavailable, got $outcome", outcome is DayDetailOutcome.CatalogueUnavailable)
    }
}
