package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `006` SC-004: opening, viewing, or navigating Insights adds no observable
 * delay to completing or undoing a task on the Today screen. Measures the
 * record path's latency before Insights has ever been touched, then again
 * immediately after exercising all three views, and asserts the second
 * measurement is not meaningfully slower.
 */
@RunWith(AndroidJUnit4::class)
class TodayRecordingNoRegressionTest : DbTestBase() {

    private fun timedMillis(block: () -> Unit): Long {
        val startNanos = System.nanoTime()
        block()
        return (System.nanoTime() - startNanos) / 1_000_000
    }

    @Test
    fun recording_after_using_insights_is_not_meaningfully_slower() = runTest {
        catalogue.seedIfNeeded()
        val today = LocalDate.parse("2026-08-15")
        time.setDate(today)
        val plan = (dayPlans.ensurePlanFor(today) as com.giraffe.mizanapp.domain.repository.EnsureOutcome.Created).plan
        val taskA = plan.plannedTasks[0].taskSlug
        val taskB = plan.plannedTasks[1].taskSlug

        val baseline = timedMillis {
            val outcome = kotlinx.coroutines.runBlocking { completions.record(today, taskA) }
            assertTrue(outcome is RecordOutcome.Recorded)
        }

        // Exercise all three Insights views once, as opening the screen would.
        kotlinx.coroutines.runBlocking {
            GetWeeklyTrend(GetHistoryPage(dayPlans, completions, catalogue, time))()
            GetMonthOverview(dayPlans, completions, catalogue, time)(YearMonth.from(today))
            GetSectionBreakdown(dayPlans, completions, catalogue, time)(
                com.giraffe.mizanapp.domain.insights.InsightsPeriod.ForMonth(YearMonth.from(today)),
            )
            GetPersonalBests(dayPlans, completions, catalogue, time)()
        }

        val afterInsights = timedMillis {
            val outcome = kotlinx.coroutines.runBlocking { completions.record(today, taskB) }
            assertTrue(outcome is RecordOutcome.Recorded)
        }

        // Generous fixed tolerance - this proves no lingering work competes
        // with the recording path, not a tight performance regression gate.
        assertTrue(
            "recording after Insights use ($afterInsights ms) must not be meaningfully slower " +
                "than before it ($baseline ms)",
            afterInsights <= baseline + 200,
        )
    }
}
