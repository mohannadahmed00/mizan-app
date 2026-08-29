package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetDaySummary
import com.giraffe.mizanapp.domain.usecase.GetWeekSummary
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The empirical proof of SC-010 and FR-024: exercising everything the sheet
 * and the day summary can do changes not one stored row. This is Principle
 * VI checked behaviourally, not just at the type level.
 */
@RunWith(AndroidJUnit4::class)
class ReadOnlySheetTest : DbTestBase() {

    private val farStart = LocalDate.parse("2026-01-05")
    private val weekStart = LocalDate.parse("2026-08-08")
    private val weekEnd = LocalDate.parse("2026-08-14")

    private data class Snapshot(
        val plans: List<Triple<String, Int, String>>, // id, availablePoints, origin
        val plannedTasks: List<Triple<String, String, Int>>, // id, taskSlug, points
        val completions: List<Triple<String, Int, Long?>>, // id, pointsAwarded, reversedAt
    )

    private suspend fun snapshot(): Snapshot {
        val plansWithTasks = db.dayPlanDao().plansBetween("0000-01-01", "9999-12-31")
        val allCompletions = db.completionDao().allBetween("0000-01-01", "9999-12-31")
        return Snapshot(
            plans = plansWithTasks.map { Triple(it.plan.id, it.plan.availablePoints, it.plan.origin) },
            plannedTasks = plansWithTasks.flatMap { it.tasks }
                .map { Triple(it.id, it.taskSlug, it.points) },
            completions = allCompletions.map { Triple(it.id, it.pointsAwarded, it.reversedAt) },
        )
    }

    @Test
    fun exercising_every_screen_control_changes_no_stored_row() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(farStart)
        dayPlans.ensurePlanFor(farStart)
        time.setDate(weekStart)
        completions.record(weekStart, "fajr-1")
        time.setDate(weekEnd) // the whole viewed week has now elapsed

        val getWeekSummary = GetWeekSummary(dayPlans, completions, catalogue, time, coverageRepo)
        val getDaySummary = GetDaySummary(dayPlans, completions)
        val currentWeek = WeekBoundary.weekContaining(time.today())
        val previousWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7))

        // Settle backfill first — the one permitted difference (new plans on
        // first view) must not appear in the before/after comparison. Both
        // weeks are touched below, so both are settled here.
        getWeekSummary(currentWeek)
        getWeekSummary(previousWeek)
        val before = snapshot()

        // Everything the sheet and the day summary can do.
        getWeekSummary(currentWeek)
        getWeekSummary(previousWeek)
        currentWeek.dates.forEach { date -> getDaySummary(date) }
        getWeekSummary(currentWeek)

        val after = snapshot()

        assertEquals("day plans must be unchanged", before.plans, after.plans)
        assertEquals("planned tasks must be unchanged", before.plannedTasks, after.plannedTasks)
        assertEquals("completions, including tombstones, must be unchanged", before.completions, after.completions)
    }
}
