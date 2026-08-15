package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.usecase.DayDetailOutcome
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.HistoryOutcome
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `005` SC-015: the first screen of history resolves within 500 ms and any
 * day within 300 ms, against three years of daily completions.
 *
 * A miss here is a finding about the storage design, not a reason to loosen
 * the budget or add a cache — `docs/PLAN.md` defers caching until a
 * measurement demands it, and this is that measurement.
 */
@RunWith(AndroidJUnit4::class)
class HistoryPerformanceTest : DbTestBase() {

    private val historyBudgetMillis = 500L
    private val dayBudgetMillis = 300L

    private suspend fun seedThreeYears(): LocalDate {
        catalogue.seedIfNeeded()
        val version = catalogue.currentVersion()!!
        val content = catalogue.catalogueAt(version)!!
        var counter = 0
        val now = 1L
        val start = LocalDate.parse("2023-08-15")

        (0L until 365L * 3).forEach { offset ->
            val date = start.plusDays(offset)
            val plan = buildDayPlan(content, version, date, PlanOrigin.OPENED) { "id-${counter++}" }
            db.dayPlanDao().insertPlanWithTasks(
                plan = plan.toEntity(now),
                tasks = plan.plannedTasks.map { it.toEntity(now) },
            )
            val task = plan.plannedTasks.first()
            db.completionDao().insert(
                Completion(
                    id = "c-${counter++}",
                    dayPlanId = plan.id,
                    taskSlug = task.taskSlug,
                    creditedDate = date,
                    pointsAwarded = task.points,
                    recordedAt = Instant.ofEpochMilli(now),
                ).toEntity(now),
            )
        }
        val last = start.plusDays(365L * 3 - 1)
        time.setDate(last)
        return last
    }

    @Test
    fun the_first_screen_of_history_resolves_within_budget() = runTest {
        seedThreeYears()
        val useCase = GetHistoryPage(dayPlans, completions, catalogue, time)

        val startNanos = System.nanoTime()
        val outcome = useCase(before = null)
        val elapsed = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("expected Ready, got $outcome", outcome is HistoryOutcome.Ready)
        assertTrue("expected under ${historyBudgetMillis}ms, took ${elapsed}ms", elapsed <= historyBudgetMillis)
    }

    @Test
    fun a_day_resolves_within_budget() = runTest {
        val last = seedThreeYears()
        val useCase = GetDayDetail(dayPlans, completions, catalogue, time)

        val startNanos = System.nanoTime()
        val outcome = useCase(last.minusDays(200))
        val elapsed = (System.nanoTime() - startNanos) / 1_000_000

        assertTrue("expected Ready, got $outcome", outcome is DayDetailOutcome.Ready)
        assertTrue("expected under ${dayBudgetMillis}ms, took ${elapsed}ms", elapsed <= dayBudgetMillis)
    }
}
