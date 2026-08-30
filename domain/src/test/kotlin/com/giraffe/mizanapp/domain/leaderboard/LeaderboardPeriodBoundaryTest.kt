package com.giraffe.mizanapp.domain.leaderboard

import com.giraffe.mizanapp.domain.time.DayBoundary
import com.giraffe.mizanapp.domain.time.WeekBoundary
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * FR-032a and FR-033: the clarification session narrowed the whole leaderboard side of this
 * feature down to period timing. `periodFor` already delegates to `WeekBoundary` and works in
 * accountability-date space, so this is a verification test, not new behaviour (FR-032b, rule 8:
 * no remote artifact is touched here). Expected outcome: no production change needed.
 */
class LeaderboardPeriodBoundaryTest {

    private val zone = ZoneId.of("Africa/Cairo")
    private val regionId = RegionId("egypt")

    // Friday 2026-03-13, Maghrib at 18:00 Cairo (16:00 UTC).
    private val fridayMaghrib = Instant.parse("2026-03-13T16:00:00Z")

    @Test
    fun weeklyPeriodMatchesTheWeekBoundarySpan() {
        val date = DayBoundary.dateAt(fridayMaghrib.plusSeconds(60), zone, fridayMaghrib)
        val period = periodFor(PeriodKind.WEEKLY, date, zone, regionId)
        val week = WeekBoundary.weekContaining(date)

        assertEquals(week.start, period.start)
        assertEquals(week.dates.last(), period.endInclusive)
    }

    @Test
    fun theTwoSidesOfFridayMaghribFallInDifferentWeeklyPeriods() {
        val beforeDate = DayBoundary.dateAt(fridayMaghrib.minusSeconds(60), zone, fridayMaghrib)
        val afterDate = DayBoundary.dateAt(fridayMaghrib.plusSeconds(60), zone, fridayMaghrib)

        val beforePeriod = periodFor(PeriodKind.WEEKLY, beforeDate, zone, regionId)
        val afterPeriod = periodFor(PeriodKind.WEEKLY, afterDate, zone, regionId)

        assertNotEquals(beforePeriod.start, afterPeriod.start)
    }

    @Test
    fun dailyAndMonthlyPeriodsAreUnaffected() {
        val date = DayBoundary.dateAt(fridayMaghrib.plusSeconds(60), zone, fridayMaghrib)

        val daily = periodFor(PeriodKind.DAILY, date, zone, regionId)
        assertEquals(date, daily.start)
        assertEquals(date, daily.endInclusive)

        val monthly = periodFor(PeriodKind.MONTHLY, date, zone, regionId)
        assertEquals(date.withDayOfMonth(1), monthly.start)
        assertEquals(date.withDayOfMonth(date.lengthOfMonth()), monthly.endInclusive)
    }
}
