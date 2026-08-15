package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.DayDetailOutcome
import com.giraffe.mizanapp.domain.usecase.GetDayDetail
import com.giraffe.mizanapp.domain.usecase.HistoryOutcome
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `005` User Story 3, the phase's defining suite (SC-005): seed history
 * under catalogue v1, introduce v2 with different points and a changed
 * schedule, and assert every previously recorded figure is unchanged while
 * today follows v2. **This is the merge gate** — if any assertion here
 * fails, the app is reading the live catalogue for a past day, which is the
 * one bug Principle III says cannot be repaired after the fact.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueChangeHistoryTest : DbTestBase() {

    private suspend fun v2() {
        val dao = db.catalogueDao()
        dao.insertVersions(listOf(CatalogueVersionEntity(version = 2, effectiveFrom = "2026-09-01")))
        val v1TaskVersions = dao.taskVersionsFor(1)
        val v2TaskVersions = v1TaskVersions.map { v1 ->
            TaskVersionEntity(
                id = UUID.randomUUID().toString(),
                taskSlug = v1.taskSlug,
                catalogueVersion = 2,
                points = v1.points + 5,
                maxOccurrencesPerDay = v1.maxOccurrencesPerDay,
                // fajr-1 moves from every day to Friday-only under v2.
                scheduleType = if (v1.taskSlug == "fajr-1") "daysOfWeek" else v1.scheduleType,
                scheduleDays = if (v1.taskSlug == "fajr-1") "FRIDAY" else v1.scheduleDays,
                updatedAt = 1L,
            )
        }
        dao.insertTaskVersions(v2TaskVersions)
    }

    @Test
    fun past_day_figures_are_unchanged_after_a_point_value_and_a_schedule_rule_change() = runTest {
        catalogue.seedIfNeeded()

        // A fully recorded week reading 500/500, under v1.
        val weekStart = LocalDate.parse("2026-08-08")
        val week = WeekBoundary.weekContaining(weekStart)
        for (date in week.dates) {
            time.setDate(date)
            val outcome = dayPlans.ensurePlanFor(date)
            val plan = (outcome as com.giraffe.mizanapp.domain.repository.EnsureOutcome.Created).plan
            for (task in plan.plannedTasks) {
                repeat(task.maxOccurrencesPerDay) { completions.record(date, task.taskSlug) }
            }
        }

        val historyUseCase = GetHistoryPage(dayPlans, completions, catalogue, time)
        val dayUseCase = GetDayDetail(dayPlans, completions, catalogue, time)

        time.setDate(week.end)
        val weekBefore = (historyUseCase(before = null) as HistoryOutcome.Ready).page.weeks
            .first { it.week.key == week.key }
        val dayBefore = (dayUseCase(week.start) as DayDetailOutcome.Ready).summary
        val recordStart = dayPlans.earliestPlanDate()
        val datesBefore = completions.observeConsistencyDates().first()
        val streakBefore = buildStreakSummary(datesBefore, time.today(), time.now(), time.zone(), recordStart)

        // Introduce v2: different points, a changed schedule, effective in the future.
        v2()

        // Every pre-change figure is unchanged - measured with the clock still
        // at week.end, so nothing here is confounded by time simply advancing
        // past a gap with nothing recorded in it (a real, separate way a
        // streak can legitimately change).
        val weekAfter = (historyUseCase(before = null) as HistoryOutcome.Ready).page.weeks
            .first { it.week.key == week.key }
        val dayAfter = (dayUseCase(week.start) as DayDetailOutcome.Ready).summary

        assertEquals("week totals must not move", weekBefore.score, weekAfter.score)
        assertEquals(
            "per-day figures within the week must not move",
            weekBefore.days.map { it.available to it.earned },
            weekAfter.days.map { it.available to it.earned },
        )
        assertEquals("the day detail must not move", dayBefore.score, dayAfter.score)
        assertEquals(
            "a completion keeps the points it was awarded",
            dayBefore.tasks.map { it.task.taskSlug to it.recordedCount },
            dayAfter.tasks.map { it.task.taskSlug to it.recordedCount },
        )

        val datesAfter = completions.observeConsistencyDates().first()
        val streakAfter = buildStreakSummary(datesAfter, time.today(), time.now(), time.zone(), recordStart)
        assertEquals("streak figures must not move after a catalogue change", streakBefore.current, streakAfter.current)
        assertEquals(streakBefore.longest, streakAfter.longest)

        // Today, moved forward past v2's effective date, follows v2. Checked
        // last and separately - this is what advancing time is for, and it
        // must not contaminate the streak comparison above.
        time.setDate(LocalDate.parse("2026-09-05")) // Saturday, after v2's effective date
        val todayOutcome = dayPlans.ensurePlanFor(time.today())
        val todayPlan = (todayOutcome as com.giraffe.mizanapp.domain.repository.EnsureOutcome.Created).plan
        assertEquals(2, todayPlan.catalogueVersion)
        assertNotEquals(
            "today's total must differ from v1's, proving it used v2",
            weekBefore.score.weekTarget,
            todayPlan.availablePoints,
        )
    }

    @Test
    fun a_plan_materialised_after_the_change_uses_the_version_effective_on_that_date() = runTest {
        catalogue.seedIfNeeded()
        time.setDate(LocalDate.parse("2026-08-08"))
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-01")) // anchor, well before the elapsed date under test

        v2()

        // "Today" is after v2's effective date, but the date being opened predates it.
        time.setDate(LocalDate.parse("2026-09-10"))
        val dayUseCase = GetDayDetail(dayPlans, completions, catalogue, time)
        val outcome = dayUseCase(LocalDate.parse("2026-08-10")) as DayDetailOutcome.Ready

        val storedPlan = dayPlans.planFor(LocalDate.parse("2026-08-10"))
        assertTrue("a plan must now be stored for this date", storedPlan != null)
        assertEquals(1, storedPlan!!.catalogueVersion)
        assertEquals(outcome.summary.score.available, storedPlan.availablePoints)
    }
}
