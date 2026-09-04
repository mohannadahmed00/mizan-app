package com.giraffe.mizanapp.domain.notification

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 9, 4, hour, minute).atZone(zone).toInstant()

    @Test fun `cross-midnight window contains late night and early morning`() {
        val quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0))
        assertTrue(quiet.contains(at(23, 30), zone)); assertTrue(quiet.contains(at(2, 0), zone)); assertFalse(quiet.contains(at(12, 0), zone))
    }

    @Test fun `same-day window contains only its interval`() {
        val quiet = QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertTrue(quiet.contains(at(13, 30), zone)); assertFalse(quiet.contains(at(12, 0), zone))
    }

    @Test fun `end after returns the close of the active window`() {
        assertEquals(at(6, 0).plus(java.time.Duration.ofDays(1)), QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)).endAfter(at(23, 30), zone))
        assertEquals(at(14, 0), QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0)).endAfter(at(13, 30), zone))
    }

    @Test fun `equal endpoints mean a full day`() {
        val quiet = QuietHours(LocalTime.NOON, LocalTime.NOON)
        assertTrue(quiet.contains(at(2, 0), zone)); assertEquals(at(12, 0).plus(java.time.Duration.ofDays(1)), quiet.endAfter(at(2, 0), zone))
    }
}
