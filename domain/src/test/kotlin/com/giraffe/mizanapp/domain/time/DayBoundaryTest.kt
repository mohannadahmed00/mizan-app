package com.giraffe.mizanapp.domain.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The accountability day runs local midnight to local midnight (FR-030).
 *
 * This rule exists in exactly one place, and this is the test of that place.
 */
class DayBoundaryTest {

    private val cairo: ZoneId = ZoneId.of("Africa/Cairo")

    private fun instantAt(date: LocalDate, hour: Int, minute: Int, second: Int, zone: ZoneId) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute, second)).atZone(zone).toInstant()

    @Test
    fun `an instant maps to its local civil date`() {
        val noon = instantAt(LocalDate.of(2026, 3, 14), 12, 0, 0, cairo)

        assertEquals(LocalDate.of(2026, 3, 14), DayBoundary.dateAt(noon, cairo))
    }

    @Test
    fun `one second before local midnight belongs to the earlier day`() {
        val justBefore = instantAt(LocalDate.of(2026, 3, 14), 23, 59, 59, cairo)

        assertEquals(LocalDate.of(2026, 3, 14), DayBoundary.dateAt(justBefore, cairo))
    }

    @Test
    fun `local midnight itself belongs to the later day`() {
        val midnight = instantAt(LocalDate.of(2026, 3, 15), 0, 0, 0, cairo)

        assertEquals(LocalDate.of(2026, 3, 15), DayBoundary.dateAt(midnight, cairo))
    }

    @Test
    fun `the same instant can be two different dates in two zones`() {
        // 00:30 on the 15th in Cairo (UTC+2) is still the afternoon of the 14th
        // in Los Angeles. The accountability date follows the device's zone.
        val justAfterMidnight = instantAt(LocalDate.of(2026, 3, 15), 0, 30, 0, cairo)

        val inCairo = DayBoundary.dateAt(justAfterMidnight, cairo)
        val inLosAngeles = DayBoundary.dateAt(justAfterMidnight, ZoneId.of("America/Los_Angeles"))

        assertEquals(LocalDate.of(2026, 3, 15), inCairo)
        assertEquals(LocalDate.of(2026, 3, 14), inLosAngeles)
        assertNotEquals("the day must follow the device's zone", inCairo, inLosAngeles)
    }
}
