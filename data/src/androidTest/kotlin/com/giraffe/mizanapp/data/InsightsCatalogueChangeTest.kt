package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetMonthOverview
import com.giraffe.mizanapp.domain.usecase.GetPersonalBests
import com.giraffe.mizanapp.domain.usecase.GetSectionBreakdown
import com.giraffe.mizanapp.domain.usecase.GetWeeklyTrend
import com.giraffe.mizanapp.domain.usecase.MonthOverviewOutcome
import com.giraffe.mizanapp.domain.usecase.GetHistoryPage
import com.giraffe.mizanapp.domain.usecase.PersonalBestsOutcome
import com.giraffe.mizanapp.domain.usecase.SectionBreakdownOutcome
import com.giraffe.mizanapp.domain.usecase.TrendOutcome
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `006` SC-003: a month or week spanning a task-catalogue content change
 * must keep every past figure exactly as it was recorded. This is the Phase
 * 6 counterpart to `005`'s `CatalogueChangeHistoryTest` — the fuller,
 * cross-view version of this scenario (trend + sections + personal bests) is
 * completed by User Story 3 (T042).
 */
@RunWith(AndroidJUnit4::class)
class InsightsCatalogueChangeTest : DbTestBase() {

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
                scheduleType = if (v1.taskSlug == "fajr-1") "daysOfWeek" else v1.scheduleType,
                scheduleDays = if (v1.taskSlug == "fajr-1") "FRIDAY" else v1.scheduleDays,
                updatedAt = 1L,
            )
        }
        dao.insertTaskVersions(v2TaskVersions)
    }

    @Test
    fun a_past_month_reads_identically_after_a_mid_record_catalogue_change() = runTest {
        catalogue.seedIfNeeded()

        // A handful of recorded days across August, under v1.
        val recordedDates = listOf(
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-08"),
            LocalDate.parse("2026-08-15"),
        )
        for (date in recordedDates) {
            time.setDate(date)
            val outcome = dayPlans.ensurePlanFor(date)
            val plan = (outcome as EnsureOutcome.Created).plan
            repeat(plan.plannedTasks.first().maxOccurrencesPerDay) {
                completions.record(date, plan.plannedTasks.first().taskSlug)
            }
        }
        time.setDate(LocalDate.parse("2026-08-31"))

        val monthOverview = GetMonthOverview(dayPlans, completions, catalogue, time, coverageRepo)
        val month = YearMonth.of(2026, 8)
        val before = (monthOverview(month) as MonthOverviewOutcome.Ready).overview

        // Introduce v2, effective in the future - it must not move August's figures.
        v2()

        val after = (monthOverview(month) as MonthOverviewOutcome.Ready).overview

        assertEquals(
            "every day's state/earned/available must be unchanged after the catalogue change",
            before.days.map { Triple(it.state, it.earned, it.available) },
            after.days.map { Triple(it.state, it.earned, it.available) },
        )
    }

    /**
     * The full SC-003 scenario from `quickstart.md`'s "The defining scenario"
     * section: trend, sections, and personal bests must all read identically
     * for a past period after a mid-record catalogue change, alongside the
     * month view already proven above.
     */
    @Test
    fun trend_sections_and_personal_bests_read_identically_after_a_mid_record_catalogue_change() = runTest {
        catalogue.seedIfNeeded()

        val weekStart = LocalDate.parse("2026-08-08")
        val week = WeekBoundary.weekContaining(weekStart)
        for (date in week.dates) {
            time.setDate(date)
            val outcome = dayPlans.ensurePlanFor(date)
            val plan = (outcome as EnsureOutcome.Created).plan
            repeat(plan.plannedTasks.first().maxOccurrencesPerDay) {
                completions.record(date, plan.plannedTasks.first().taskSlug)
            }
        }
        time.setDate(week.end)

        val trendUseCase = GetWeeklyTrend(GetHistoryPage(dayPlans, completions, catalogue, time, coverageRepo))
        val sectionsUseCase = GetSectionBreakdown(dayPlans, completions, catalogue, time, coverageRepo)
        val bestsUseCase = GetPersonalBests(dayPlans, completions, catalogue, time, coverageRepo)

        val trendBefore = (trendUseCase() as TrendOutcome.Ready).weeks.first { it.week.key == week.key }.score
        val sectionsBefore = (sectionsUseCase(InsightsPeriod.ForWeek(week)) as SectionBreakdownOutcome.Ready).sections
        val bestsBefore = (bestsUseCase() as PersonalBestsOutcome.Ready).bests

        v2()

        val trendAfter = (trendUseCase() as TrendOutcome.Ready).weeks.first { it.week.key == week.key }.score
        val sectionsAfter = (sectionsUseCase(InsightsPeriod.ForWeek(week)) as SectionBreakdownOutcome.Ready).sections
        val bestsAfter = (bestsUseCase() as PersonalBestsOutcome.Ready).bests

        assertEquals("trend week score must not move", trendBefore, trendAfter)
        assertEquals(
            "section rates must not move",
            sectionsBefore.map { it.sectionId to (it.completed to it.available) },
            sectionsAfter.map { it.sectionId to (it.completed to it.available) },
        )
        assertEquals("the best day must not move", bestsBefore.bestDay, bestsAfter.bestDay)
        assertEquals("the best week must not move", bestsBefore.bestWeek, bestsAfter.bestWeek)

        // Today, moved past v2's effective date, follows v2.
        time.setDate(LocalDate.parse("2026-09-05"))
        val todayOutcome = dayPlans.ensurePlanFor(time.today())
        val todayPlan = (todayOutcome as EnsureOutcome.Created).plan
        assertEquals(2, todayPlan.catalogueVersion)
    }
}
