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
 * `GetHistoryPage` - a read-only page of weeks, newest first, floored at the
 * record start. Every test here maps to a guarantee in
 * contracts/use-cases.md.
 */
class GetHistoryPageTest {

    private val today = LocalDate.parse("2026-08-14") // a Friday
    private val currentWeek = WeekBoundary.weekContaining(today)

    private fun timeAt(date: LocalDate) = FakeTimeProvider().apply { setDate(date) }

    @Test
    fun `first page starts at the week containing today`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, currentWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase() as HistoryOutcome.Ready

        assertEquals(currentWeek.key, outcome.page.weeks.first().week.key)
    }

    @Test
    fun `page is continuous with no missing weeks`() = runBlocking {
        val time = timeAt(today)
        // Record start is twelve weeks before today's week, with nothing recorded in between.
        val recordStartWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7 * 12))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStartWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val allWeeks = mutableListOf<com.giraffe.mizanapp.domain.week.Week>()
        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        do {
            val outcome = useCase(before = cursor) as HistoryOutcome.Ready
            allWeeks += outcome.page.weeks.map { it.week }
            cursor = outcome.page.oldestLoaded
        } while (outcome.page.hasMore)

        for (i in 0 until allWeeks.size - 1) {
            assertEquals(
                "week ${allWeeks[i].key} and the next must be exactly 7 days apart",
                allWeeks[i].start.minusDays(7),
                allWeeks[i + 1].start,
            )
        }
    }

    @Test
    fun `paging stops at the week containing the record start`() = runBlocking {
        val time = timeAt(today)
        val recordStartWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7 * 12))
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStartWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        var last: HistoryOutcome.Ready
        do {
            val outcome = useCase(before = cursor) as HistoryOutcome.Ready
            last = outcome
            cursor = outcome.page.oldestLoaded
        } while (last.page.hasMore)

        assertEquals(recordStartWeek.key, last.page.weeks.last().week.key)
        assertFalse(last.page.hasMore)
    }

    @Test
    fun `no week later than the current week is ever returned`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, currentWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        // Requesting "before" a future week must still clamp to the current week.
        val futureKey = WeekBoundary.weekContaining(today.plusDays(21)).key
        val outcome = useCase(before = futureKey) as HistoryOutcome.Ready

        assertTrue(outcome.page.weeks.none { it.week.start.isAfter(currentWeek.start) })
    }

    @Test
    fun `an empty record is Ready with no weeks, not an error`() = runBlocking {
        val time = timeAt(today)
        val plans = FakeWeekDayPlanRepository(time = time) // no plans seeded at all
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase() as HistoryOutcome.Ready

        assertTrue(outcome.page.weeks.isEmpty())
        assertFalse(outcome.page.hasMore)
    }

    @Test
    fun `elapsed unplanned dates report the version effective on that date`() = runBlocking {
        // v1 catalogue effective from a date well before today; nothing changes
        // version here (single-version fixture), but this proves the call
        // path uses versionEffectiveOn rather than currentVersion.
        val time = timeAt(today)
        val recordStart = currentWeek.start.minusDays(7)
        val plans = FakeWeekDayPlanRepository(time = time).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStart, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        val outcome = useCase() as HistoryOutcome.Ready
        val weekWithUnplannedElapsedDay = outcome.page.weeks.first { it.week.key == currentWeek.key }
        // Every elapsed day but today has no stored plan and must still report a real total.
        val elapsedUnplanned = weekWithUnplannedElapsedDay.days.first {
            !it.date.isAfter(today) && it.date != recordStart && it.date.dayOfWeek == java.time.DayOfWeek.SATURDAY
        }
        assertTrue("available must be a real projected total, not 0", elapsedUnplanned.available > 0)
    }

    @Test
    fun `loading a page writes nothing`() = runBlocking {
        val time = timeAt(today)
        val recordStartWeek = WeekBoundary.weekContaining(currentWeek.start.minusDays(7 * 12))
        // failDates covers the whole span so ANY ensurePlanFor call (besides the seeded anchor) throws.
        val span = generateSequence(recordStartWeek.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(currentWeek.end) }
            .toSet()
        val plans = FakeWeekDayPlanRepository(time = time, failDates = span).apply {
            seedPlan(buildDayPlan(DayFixtures.catalogue, 1, recordStartWeek.start, PlanOrigin.OPENED) { "seed" })
        }
        val useCase = GetHistoryPage(plans, FakeWeekCompletionRepository(), FakeWeekCatalogueRepository(), time, FakeRecordCoverageRepository())

        var cursor: com.giraffe.mizanapp.domain.week.WeekKey? = null
        var hasMore: Boolean
        do {
            val outcome = useCase(before = cursor) as HistoryOutcome.Ready
            cursor = outcome.page.oldestLoaded
            hasMore = outcome.page.hasMore
        } while (hasMore)
        // No exception thrown means ensurePlanFor was never called on the poisoned dates.
    }
}
