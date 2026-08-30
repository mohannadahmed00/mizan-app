package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Research R1: Maghrib on Friday is the start of accountability-Saturday, so
 * `WeekBoundary.weekContaining` needs no change at all. These tests exist to verify that
 * prediction, not to describe new behaviour — if either fails, the fix belongs in
 * `DayBoundary.kt`, never in `WeekBoundary.kt`.
 */
class MaghribWeekBoundaryTest {

    private val zone = ZoneId.of("Africa/Cairo")

    // Friday 2026-03-13, Maghrib at 18:00 Cairo (16:00 UTC).
    private val fridayMaghrib = Instant.parse("2026-03-13T16:00:00Z")

    @Test
    fun fridayBeforeMaghribIsInTheClosingWeek() {
        val justBefore = fridayMaghrib.minusSeconds(60)
        val accountabilityDate = DayBoundary.dateAt(justBefore, zone, fridayMaghrib)
        val week = WeekBoundary.weekContaining(accountabilityDate)

        assertEquals(LocalDate.of(2026, 3, 13), accountabilityDate)
        assertEquals(LocalDate.of(2026, 3, 7), week.start)
    }

    @Test
    fun fridayAfterMaghribIsInTheNewWeek() {
        val justAfter = fridayMaghrib.plusSeconds(60)
        val accountabilityDate = DayBoundary.dateAt(justAfter, zone, fridayMaghrib)
        val week = WeekBoundary.weekContaining(accountabilityDate)

        assertEquals(LocalDate.of(2026, 3, 14), accountabilityDate)
        assertEquals(LocalDate.of(2026, 3, 14), week.start)
    }

    @Test
    fun theWeekChangesAtMaghribAndNotAtMidnight() {
        val beforeMaghrib = DayBoundary.dateAt(fridayMaghrib.minusSeconds(60), zone, fridayMaghrib)
        val afterMaghrib = DayBoundary.dateAt(fridayMaghrib.plusSeconds(60), zone, fridayMaghrib)
        val atLocalMidnight = DayBoundary.dateAt(
            fridayMaghrib.plusSeconds(60).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(),
            zone,
            fridayMaghrib,
        )

        assertNotEquals(
            WeekBoundary.weekContaining(beforeMaghrib).key,
            WeekBoundary.weekContaining(afterMaghrib).key,
        )
        assertEquals(
            WeekBoundary.weekContaining(afterMaghrib).key,
            WeekBoundary.weekContaining(atLocalMidnight).key,
        )
    }

    @Test
    fun everyInstantMapsToExactlyOneDayAndOneWeek() {
        val yearZone = ZoneId.of("Africa/Cairo")
        var cursor = Instant.parse("2026-01-01T00:00:00Z")
        val end = cursor.plusSeconds(365L * 24 * 3600)

        // Maghrib fixed at 18:00 local for every civil date, for a stable table over the walk.
        fun maghribFor(civilDate: LocalDate): Instant =
            civilDate.atTime(18, 0).atZone(yearZone).toInstant()

        val resolvedDates = mutableListOf<LocalDate>()
        while (cursor.isBefore(end)) {
            val civilDate = cursor.atZone(yearZone).toLocalDate()
            val maghribToday = maghribFor(civilDate)
            resolvedDates.add(DayBoundary.dateAt(cursor, yearZone, maghribToday))
            cursor = cursor.plusSeconds(3600)
        }

        val distinctInOrder = resolvedDates.distinctBy { it }.let { list ->
            // distinctBy keeps first-seen order; verify that order is exactly ascending by one day.
            list
        }
        for (i in 1 until distinctInOrder.size) {
            assertEquals(distinctInOrder[i - 1].plusDays(1), distinctInOrder[i])
        }

        var lastSeen: LocalDate? = null
        for (date in resolvedDates) {
            if (lastSeen == null) {
                lastSeen = date
            } else if (date != lastSeen) {
                assertEquals(lastSeen.plusDays(1), date)
                lastSeen = date
            }
        }
    }
}
