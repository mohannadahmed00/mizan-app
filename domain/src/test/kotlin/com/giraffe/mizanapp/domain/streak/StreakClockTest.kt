package com.giraffe.mizanapp.domain.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakClockTest {

    private val zone = ZoneId.of("Africa/Cairo")
    private val day = LocalDate.parse("2026-08-19")

    private fun at(hour: Int, minute: Int): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun before_twenty_hundred_is_not_at_risk() {
        assertFalse(StreakClock.isAtRiskWindow(at(19, 59), zone))
    }

    @Test
    fun exactly_twenty_hundred_is_at_risk_inclusive() {
        assertTrue(StreakClock.isAtRiskWindow(at(20, 0), zone))
    }

    @Test
    fun late_evening_is_at_risk() {
        assertTrue(StreakClock.isAtRiskWindow(at(23, 59), zone))
    }

    @Test
    fun midnight_and_morning_are_not_at_risk() {
        assertFalse(StreakClock.isAtRiskWindow(at(0, 0), zone))
        assertFalse(StreakClock.isAtRiskWindow(at(9, 0), zone))
    }

    @Test
    fun next_boundary_from_morning_is_todays_twenty_hundred() {
        val result = StreakClock.nextBoundaryAfter(at(9, 0), zone)
        assertEquals(day.atTime(20, 0).atZone(zone).toInstant(), result)
    }

    @Test
    fun next_boundary_from_twenty_hundred_is_next_midnight() {
        val result = StreakClock.nextBoundaryAfter(at(20, 0), zone)
        assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant(), result)
    }

    @Test
    fun next_boundary_from_late_evening_is_next_midnight() {
        val result = StreakClock.nextBoundaryAfter(at(23, 59), zone)
        assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant(), result)
    }

    @Test
    fun every_returned_boundary_is_strictly_after_the_instant_passed_in() {
        listOf(at(0, 0), at(9, 0), at(19, 59), at(20, 0), at(23, 59)).forEach { now ->
            val boundary = StreakClock.nextBoundaryAfter(now, zone)
            assertTrue("$boundary must be strictly after $now", boundary.isAfter(now))
        }
    }
}
