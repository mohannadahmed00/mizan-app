package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetMonthOverview` - a read-only month, no write on read (FR-006, FR-009).
 * Uses the same in-memory fakes `GetHistoryPageTest`/`GetWeekSummaryTest`
 * (`:domain`) use.
 */
class GetMonthOverviewTest {

    private fun timeAt(date: LocalDate) = FakeTimeProvider().apply { setDate(date) }

    @Test
    fun `a month entirely covered by stored plans returns their own figures unchanged`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-05"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-05"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase(YearMonth.of(2026, 8)) as MonthOverviewOutcome.Ready

        val storedDay = outcome.overview.days.single { it.date == LocalDate.parse("2026-08-05") }
        assertEquals(69, storedDay.available) // Wednesday base total from the fixture
    }

    @Test
    fun `an elapsed date with no stored plan is projected with the version effective on that date`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase(YearMonth.of(2026, 8)) as MonthOverviewOutcome.Ready

        val unplanned = outcome.overview.days.single { it.date == LocalDate.parse("2026-08-05") }
        assertTrue("an elapsed unplanned date must still report a real projected total", unplanned.available > 0)
    }

    @Test
    fun `a future date in the current month is projected with the current version`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase(YearMonth.of(2026, 8)) as MonthOverviewOutcome.Ready

        val future = outcome.overview.days.single { it.date == LocalDate.parse("2026-08-20") }
        assertEquals(DayCellState.NOT_YET_ELAPSED, future.state)
        assertTrue(future.available > 0)
    }

    @Test
    fun `a date before the record start gets no projection and reads outside the record`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-20"))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-15"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase(YearMonth.of(2026, 8)) as MonthOverviewOutcome.Ready

        val beforeStart = outcome.overview.days.single { it.date == LocalDate.parse("2026-08-01") }
        assertEquals(DayCellState.OUTSIDE_RECORD, beforeStart.state)
        assertEquals(0, beforeStart.available)
    }

    @Test
    fun `ensurePlanFor is never called - reading a month writes nothing`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-20"))
        val span = generateSequence(LocalDate.parse("2026-08-01")) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.parse("2026-08-31")) }.toSet()
        val plans = FakeWeekDayPlanRepository(time = time, failDates = span).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, LocalDate.parse("2026-08-01"), PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        // No exception means ensurePlanFor was never called on any poisoned date.
        useCase(YearMonth.of(2026, 8))
        Unit
    }

    @Test
    fun `missing catalogue version surfaces CatalogueUnavailable`() = runBlocking {
        val time = timeAt(LocalDate.parse("2026-08-10"))
        val plans = FakeWeekDayPlanRepository(time = time)
        val useCase = GetMonthOverview(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(available = false), time, FakeRecordCoverageRepository())

        val outcome = useCase(YearMonth.of(2026, 8))

        assertTrue(outcome is MonthOverviewOutcome.CatalogueUnavailable)
    }
}
