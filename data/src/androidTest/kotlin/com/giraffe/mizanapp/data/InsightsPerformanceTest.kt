package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.domain.usecase.MonthOverviewOutcome
import com.giraffe.mizanapp.domain.usecase.PersonalBestsOutcome
import com.giraffe.mizanapp.domain.usecase.SectionBreakdownOutcome
import com.giraffe.mizanapp.domain.usecase.TrendOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `006` SC-002: each of the three Insights views renders within 1 second
 * against a full year of daily completions. `GetPersonalBests` is the one
 * full-record read in this feature and gets its own budget against a
 * 3-year fixture, mirroring `005`'s `HistoryPerformanceTest` scale
 * (research.md "Full-record scan bound for GetPersonalBests").
 */
@RunWith(AndroidJUnit4::class)
class InsightsPerformanceTest : DbTestBase() {

    private val viewBudgetMillis = 1_000L

    private suspend fun seedDays(days: Long, start: LocalDate): LocalDate {
        catalogue.seedIfNeeded()
        val version = catalogue.currentVersion()!!
        val content = catalogue.catalogueAt(version)!!
        var counter = 0
        val now = 1L

        (0L until days).forEach { offset ->
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
        val last = start.plusDays(days - 1)
        time.setDate(last)
        return last
    }

    private inline fun timedMillis(block: () -> Unit): Long {
        val startNanos = System.nanoTime()
        block()
        return (System.nanoTime() - startNanos) / 1_000_000
    }

    @Test
    fun weekly_trend_resolves_within_budget_over_a_year() = runTest {
        seedDays(365, LocalDate.parse("2025-08-15"))
        val useCase = GetWeeklyTrend(GetHistoryPage(dayPlans, completions, catalogue, time))

        var outcome: TrendOutcome? = null
        val elapsed = timedMillis { outcome = useCase() }

        assertTrue("expected Ready, got $outcome", outcome is TrendOutcome.Ready)
        assertTrue("expected under ${viewBudgetMillis}ms, took ${elapsed}ms", elapsed <= viewBudgetMillis)
    }

    @Test
    fun monthly_overview_resolves_within_budget_over_a_year() = runTest {
        val last = seedDays(365, LocalDate.parse("2025-08-15"))
        val useCase = GetMonthOverview(dayPlans, completions, catalogue, time)

        var outcome: MonthOverviewOutcome? = null
        val elapsed = timedMillis { outcome = useCase(YearMonth.from(last)) }

        assertTrue("expected Ready, got $outcome", outcome is MonthOverviewOutcome.Ready)
        assertTrue("expected under ${viewBudgetMillis}ms, took ${elapsed}ms", elapsed <= viewBudgetMillis)
    }

    @Test
    fun section_breakdown_resolves_within_budget_over_a_year() = runTest {
        val last = seedDays(365, LocalDate.parse("2025-08-15"))
        val useCase = GetSectionBreakdown(dayPlans, completions, catalogue, time)

        var outcome: SectionBreakdownOutcome? = null
        val elapsed = timedMillis { outcome = useCase(InsightsPeriod.ForMonth(YearMonth.from(last))) }

        assertTrue("expected Ready, got $outcome", outcome is SectionBreakdownOutcome.Ready)
        assertTrue("expected under ${viewBudgetMillis}ms, took ${elapsed}ms", elapsed <= viewBudgetMillis)
    }

    @Test
    fun personal_bests_resolves_within_budget_over_three_years() = runTest {
        seedDays(365L * 3, LocalDate.parse("2023-08-15"))
        val useCase = GetPersonalBests(dayPlans, completions, catalogue, time)

        var outcome: PersonalBestsOutcome? = null
        val elapsed = timedMillis { outcome = useCase() }

        assertTrue("expected Ready, got $outcome", outcome is PersonalBestsOutcome.Ready)
        assertTrue("expected under ${viewBudgetMillis}ms, took ${elapsed}ms", elapsed <= viewBudgetMillis)
    }
}
