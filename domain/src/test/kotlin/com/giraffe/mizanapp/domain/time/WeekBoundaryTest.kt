package com.giraffe.mizanapp.domain.time

import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The week runs Saturday through Friday (FR-001, Principle VII). This rule
 * exists in exactly one place, and this is the test of that place.
 */
class WeekBoundaryTest {

    @Test
    fun `a Saturday is the start of its own week`() {
        assertEquals(LocalDate.parse("2026-08-08"), WeekBoundary.startOfWeek(LocalDate.parse("2026-08-08")))
    }

    @Test
    fun `a Friday belongs to the week that began the previous Saturday`() {
        assertEquals(LocalDate.parse("2026-08-08"), WeekBoundary.startOfWeek(LocalDate.parse("2026-08-14")))
    }

    @Test
    fun `every date in the week resolves to the same start`() {
        val expected = LocalDate.parse("2026-08-08")
        val week = (0..6).map { expected.plusDays(it.toLong()) }

        week.forEach { date ->
            assertEquals("failed for $date", expected, WeekBoundary.startOfWeek(date))
        }
    }

    @Test
    fun `the next Saturday starts a new week`() {
        assertEquals(LocalDate.parse("2026-08-15"), WeekBoundary.startOfWeek(LocalDate.parse("2026-08-15")))
    }

    @Test
    fun `a week can cross both a month boundary and a year boundary`() {
        val week = WeekBoundary.weekContaining(LocalDate.parse("2026-12-30"))

        val expectedDates = listOf(
            "2026-12-26", "2026-12-27", "2026-12-28", "2026-12-29",
            "2026-12-30", "2026-12-31", "2027-01-01",
        ).map(LocalDate::parse)

        assertEquals(expectedDates, week.dates)
    }

    @Test
    fun `every date in a boundary-crossing week shares the same key`() {
        val expectedKey = WeekKey("2026-12-26")
        val week = WeekBoundary.weekContaining(LocalDate.parse("2026-12-30"))

        week.dates.forEach { date ->
            assertEquals("failed for $date", expectedKey, WeekBoundary.weekContaining(date).key)
        }
    }
}
