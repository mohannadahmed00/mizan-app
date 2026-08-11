package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import com.giraffe.mizanapp.domain.usecase.WeekOutcome
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SC-013: opening a week renders final figures within 300 ms, whether it
 * needs a full backfill or reads a store seeded with a year of history.
 *
 * A miss here is a finding about `002`'s storage design, not a reason to
 * loosen the budget or add a cache — `docs/PLAN.md` defers caching until a
 * measurement demands it, and this is that measurement.
 */
@RunWith(AndroidJUnit4::class)
class WeekPerformanceTest : DbTestBase() {

    private val budgetMillis = 300L

    @Test
    fun a_week_needing_all_seven_days_backfilled_renders_within_budget() = runTest {
        catalogue.seedIfNeeded()
        // Anchor the record far enough back that the whole viewed week is
        // eligible for backfill, then move to that week's Friday.
        time.setDate(LocalDate.parse("2026-01-05"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-01-05"))
        time.setDate(LocalDate.parse("2026-08-14")) // Friday: the whole week has elapsed

        val useCase = GetWeekSummary(dayPlans, completions, catalogue, time)
        val week = WeekBoundary.weekContaining(time.today())

        val elapsed = measureMillis { useCase(week) }

        assertTrue("expected under ${budgetMillis}ms, took ${elapsed}ms", elapsed <= budgetMillis)
    }

    @Test
    fun a_no_backfill_week_against_a_year_of_history_renders_within_budget() = runTest {
        catalogue.seedIfNeeded()
        val version = catalogue.currentVersion()!!
        val content = catalogue.catalogueAt(version)!!
        var counter = 0
        val now = 1L

        // Direct bulk insert — one year, no plan missing, so this exercises
        // the read path alone, not backfill.
        val start = LocalDate.parse("2025-08-15")
        (0L until 365L).forEach { offset ->
            val date = start.plusDays(offset)
            val plan = buildDayPlan(content, version, date, PlanOrigin.OPENED) { "id-${counter++}" }
            db.dayPlanDao().insertPlanWithTasks(
                plan = plan.toEntity(now),
                tasks = plan.plannedTasks.map { it.toEntity(now) },
            )
        }
        time.setDate(start.plusDays(364)) // the last seeded date, a fully elapsed week behind it

        val useCase = GetWeekSummary(dayPlans, completions, catalogue, time)
        val week = WeekBoundary.weekContaining(time.today())

        val elapsed = measureMillis { useCase(week) }

        assertTrue("expected under ${budgetMillis}ms, took ${elapsed}ms", elapsed <= budgetMillis)
    }

    private suspend fun measureMillis(block: suspend () -> WeekOutcome): Long {
        val startNanos = System.nanoTime()
        val outcome = block()
        val elapsed = (System.nanoTime() - startNanos) / 1_000_000
        assertTrue("expected Ready, got $outcome", outcome is WeekOutcome.Ready)
        return elapsed
    }
}
