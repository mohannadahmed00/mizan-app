package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayFixtures
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GetWeeklyTrend` - `GetHistoryPage`, reordered (research.md R1). No new
 * aggregation is tested here; `GetHistoryPage` already has its own tests
 * (`005`) - these prove only that `GetWeeklyTrend` calls it correctly and
 * reverses/paginates as the trend chart needs.
 *
 * No mocking library is used in this project; `GetHistoryPage` is exercised
 * for real, backed by the same in-memory fakes `GetHistoryPageTest` uses.
 */
class GetWeeklyTrendTest {

    private val today = LocalDate.parse("2026-08-14") // a Friday
    private val currentWeek = WeekBoundary.weekContaining(today)

    private fun timeAt(date: LocalDate) = FakeTimeProvider().apply { setDate(date) }

    private fun trendFor(
        time: FakeTimeProvider,
        plans: FakeWeekDayPlanRepository,
        catalogueAvailable: Boolean = true,
    ): GetWeeklyTrend {
        val historyPage = GetHistoryPage(
            plans,
            FakeWeekCompletionRepository(),
            FakeWeekCatalogueRepository(available = catalogueAvailable),
            time,
        )
        return GetWeeklyTrend(historyPage)
    }

    @Test
    fun `initial call loads the most recent weeks reversed to oldest-first`() = runBlocking {
        val time = timeAt(today)
        val recordStartWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7 * 3))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStartWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = trendFor(time, plans)

        val outcome = useCase() as TrendOutcome.Ready

        // Oldest-first: the first element must not be after the last.
        assertTrue(outcome.weeks.first().week.start.isBefore(outcome.weeks.last().week.start) || outcome.weeks.size == 1)
        assertEquals(currentWeek.key, outcome.weeks.last().week.key)
    }

    @Test
    fun `history shorter than the default window returns however many weeks exist`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, currentWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = trendFor(time, plans)

        val outcome = useCase() as TrendOutcome.Ready

        assertEquals(1, outcome.weeks.size)
        assertFalse(outcome.hasMore)
    }

    @Test
    fun `passing before fetches an older page for scrolling back`() = runBlocking {
        val time = timeAt(today)
        val recordStartWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7 * 20))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStartWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = trendFor(time, plans)

        val first = useCase() as TrendOutcome.Ready
        val oldestLoaded = first.weeks.first().week.key // oldest-first: index 0 is oldest
        val second = useCase(before = oldestLoaded) as TrendOutcome.Ready

        // The second page's weeks are strictly older than the first page's oldest.
        assertTrue(second.weeks.last().week.start.isBefore(first.weeks.first().week.start))
    }

    @Test
    fun `hasMore is false once the record-start week has been reached`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, currentWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = trendFor(time, plans)

        val outcome = useCase() as TrendOutcome.Ready

        assertFalse(outcome.hasMore)
    }

    @Test
    fun `an empty record returns NoHistory`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time)
        val useCase = trendFor(time, plans)

        val outcome = useCase()

        assertEquals(TrendOutcome.NoHistory, outcome)
    }

    @Test
    fun `missing catalogue version surfaces CatalogueUnavailable`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, currentWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = trendFor(time, plans, catalogueAvailable = false)

        val outcome = useCase()

        assertTrue(outcome is TrendOutcome.CatalogueUnavailable)
    }
}
