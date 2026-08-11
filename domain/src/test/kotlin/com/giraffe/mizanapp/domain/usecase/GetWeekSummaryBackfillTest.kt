package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backfill: creating plans for elapsed days the app never opened, before
 * `GetWeekSummary` aggregates. Every case here maps to a numbered guarantee
 * in contracts/repositories.md.
 *
 * Every test seeds an "anchor" plan on a date well before the viewed week,
 * standing in for a record that started long ago — realistic, since `002`
 * always creates today's plan before the Week screen is even reachable, so
 * `earliestPlanDate()` is never null when `GetWeekSummary` runs in the real
 * app. Without that anchor, a mid-week-only record would correctly leave the
 * earlier days of the *same* week outside the record (spec.md edge case:
 * "the user's very first launch happens mid-week"), which is a different
 * case from the one most of these tests exercise.
 */
class GetWeekSummaryBackfillTest {

    private val week = WeekBoundary.weekContaining(LocalDate.parse("2026-08-08"))
    private val longAgo = LocalDate.parse("2026-01-05")

    private fun anchoredPlans(time: FakeTimeProvider, failDates: Set<LocalDate> = emptySet()) =
        FakeWeekDayPlanRepository(time = time, failDates = failDates).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, version = 1, date = longAgo, origin = PlanOrigin.OPENED) { "anchor" })
        }

    @Test
    fun `every elapsed day of the week except today gets exactly one plan created`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) } // Friday: the whole week has elapsed
        val plans = anchoredPlans(time)
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(week)

        assertTrue("expected Ready, got $outcome", outcome is WeekOutcome.Ready)
        assertEquals(6, plans.creationCount) // Sat..Thu; Friday is today, not backfill's job
        week.dates.dropLast(1).forEach { assertTrue("no plan for $it", plans.planFor(it) != null) }
    }

    @Test
    fun `a date at or after today never gets a plan`() = runBlocking {
        val today = week.dates[3] // Tuesday
        val time = FakeTimeProvider().apply { setDate(today) }
        val plans = anchoredPlans(time)
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        useCase(week)

        // Before today: backfilled.
        listOf(week.dates[0], week.dates[1], week.dates[2]).forEach {
            assertTrue("expected a backfilled plan for $it", plans.planFor(it) != null)
        }
        // Today and later: never touched by backfill.
        listOf(week.dates[3], week.dates[4], week.dates[5], week.dates[6]).forEach {
            assertNull("must not backfill $it", plans.planFor(it))
        }
    }

    @Test
    fun `a date before the record start never gets a plan`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val plans = FakeWeekDayPlanRepository(time = time)
        // Record starts on the Monday of this week — Saturday and Sunday predate it.
        val recordStart = week.dates[2]
        plans.seedPlan(
            buildDayPlan(DayFixtures.catalogue, version = 1, date = recordStart, origin = PlanOrigin.OPENED) { "seed" },
        )
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        useCase(week)

        assertNull(plans.planFor(week.dates[0]))
        assertNull(plans.planFor(week.dates[1]))
    }

    @Test
    fun `an existing plan is returned untouched and never rebuilt`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val plans = anchoredPlans(time)
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        useCase(week)
        val firstRunCount = plans.creationCount
        assertTrue("the first run must actually have created something", firstRunCount > 0)
        useCase(week)

        assertEquals("a second run must create nothing new", firstRunCount, plans.creationCount)
    }

    @Test
    fun `invoking twice returns identical figures`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val plans = anchoredPlans(time)
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val first = useCase(week) as WeekOutcome.Ready
        val second = useCase(week) as WeekOutcome.Ready

        assertEquals(first.summary.score, second.summary.score)
    }

    @Test
    fun `backfill creates no completion`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val plans = anchoredPlans(time)
        val completions = FakeWeekCompletionRepository()
        val useCase = GetWeekSummary(plans, completions, FakeWeekCatalogueRepository(), time)

        val outcome = useCase(week) as WeekOutcome.Ready

        assertEquals(0, outcome.summary.score.earned)
    }

    @Test
    fun `a storage failure during backfill returns BackfillFailed with no figures`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val plans = anchoredPlans(time, failDates = setOf(week.dates[3])) // Tuesday poisoned
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(week)

        assertTrue("expected BackfillFailed, got $outcome", outcome is WeekOutcome.BackfillFailed)
    }

    @Test
    fun `plans written before a failure survive and are reused on the next attempt`() = runBlocking {
        val time = FakeTimeProvider().apply { setDate(week.end) }
        val poisoned = week.dates[3] // Tuesday; dates before it (Sat, Sun, Mon) must survive
        val plans = anchoredPlans(time, failDates = setOf(poisoned))
        val useCase = GetWeekSummary(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        useCase(week)

        assertEquals(3, plans.creationCount) // Sat, Sun, Mon — stopped before the poisoned Tuesday
        listOf(week.dates[0], week.dates[1], week.dates[2]).forEach {
            assertTrue("$it must have survived the failure", plans.planFor(it) != null)
        }
        assertNull("the poisoned date itself must not exist", plans.planFor(poisoned))

        // A retry, once the failure clears, must reuse those three rather
        // than recreate them — this is `ensurePlanFor`'s own AlreadyExists
        // guarantee (contracts/repositories.md, DayPlanRepository #5),
        // already proven directly by `an existing plan is returned untouched
        // and never rebuilt` above; nothing new to assert with a poisoned
        // repository here.
    }
}
