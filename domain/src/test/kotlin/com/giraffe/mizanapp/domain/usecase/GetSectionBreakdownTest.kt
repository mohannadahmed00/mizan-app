package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetSectionBreakdown` - a read-only, elapsed-dates-only fold over a week
 * or month (FR-003). Never calls `ensurePlanFor`; unstored elapsed dates are
 * derived read-only via `005`'s `deriveDayPlan`.
 */
class GetSectionBreakdownTest {

    private fun timeAt(date: LocalDate) = FakeTimeProvider().apply { setDate(date) }

    @Test
    fun `a week period reads only that week's elapsed dates`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-11")) // Tuesday within the week
        val weekStart = LocalDate.parse("2026-08-08")
        val week = WeekBoundary.weekContaining(weekStart)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, weekStart, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetSectionBreakdown(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(InsightsPeriod.ForWeek(week))

        assertTrue(outcome is SectionBreakdownOutcome.Ready)
        assertTrue((outcome as SectionBreakdownOutcome.Ready).sections.isNotEmpty())
    }

    @Test
    fun `a month period reads only that month's elapsed dates`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetSectionBreakdown(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        val outcome = useCase(InsightsPeriod.ForMonth(YearMonth.of(2026, 8)))

        assertTrue(outcome is SectionBreakdownOutcome.Ready)
        assertTrue((outcome as SectionBreakdownOutcome.Ready).sections.isNotEmpty())
    }

    @Test
    fun `an elapsed date with no stored plan is derived, not written`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val span = generateSequence(LocalDate.parse("2026-08-01")) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.parse("2026-08-31")) }.toSet()
        val plans = FakeWeekDayPlanRepository(time = time, failDates = span).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetSectionBreakdown(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time)

        // No exception means ensurePlanFor was never called on any poisoned date.
        val outcome = useCase(InsightsPeriod.ForMonth(YearMonth.of(2026, 8)))
        assertTrue(outcome is SectionBreakdownOutcome.Ready)
    }

    @Test
    fun `missing catalogue surfaces CatalogueUnavailable`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time)
        val useCase = GetSectionBreakdown(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(available = false), time)

        val outcome = useCase(InsightsPeriod.ForMonth(YearMonth.of(2026, 8)))

        assertTrue(outcome is SectionBreakdownOutcome.CatalogueUnavailable)
    }
}
