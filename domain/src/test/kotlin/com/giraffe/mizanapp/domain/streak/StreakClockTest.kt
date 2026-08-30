package com.giraffe.mizanapp.domain.streak

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `StreakClock` now takes `dayEndsAt` — the boundary's own `expiresAt` (FR-029) — rather than
 * reading a fixed 20:00 against the zone itself. It stays a pure function of instants passed in;
 * a Maghrib day's actual length is `:data`'s concern, not this object's.
 */
class StreakClockTest {

    private val zone = ZoneId.of("Africa/Cairo")

    /** A dayEndsAt sequence with realistic drift: `amplitudeHours` around 18:00 local per day. */
    private fun maghribSeries(startDate: LocalDate, days: Int, amplitudeHours: Double): List<Instant> =
        (0 until days).map { i ->
            val date = startDate.plusDays(i.toLong())
            val driftHours = amplitudeHours * sin(2 * Math.PI * i / 365.0)
            val minutesFromMidnight = ((18 + driftHours) * 60).toLong()
            date.atStartOfDay(zone).toInstant().plusSeconds(minutesFromMidnight * 60)
        }

    @Test
    fun atRiskPointIsAlwaysInsideItsOwnDay() {
        // A low-latitude profile (small drift) and a high-latitude one (large drift, but still
        // leaving well over four hours of day length either side).
        val lowLatitude = maghribSeries(LocalDate.parse("2026-01-01"), 365, amplitudeHours = 0.5)
        val highLatitude = maghribSeries(LocalDate.parse("2026-01-01"), 365, amplitudeHours = 3.5)

        for (series in listOf(lowLatitude, highLatitude)) {
            for (i in 1 until series.size) {
                val previousDayEndsAt = series[i - 1]
                val dayEndsAt = series[i]
                val atRiskInstant = dayEndsAt.minus(StreakClock.AT_RISK_BEFORE_END)

                assertTrue(
                    "at-risk instant $atRiskInstant must fall after the previous day ended " +
                        "($previousDayEndsAt)",
                    atRiskInstant.isAfter(previousDayEndsAt),
                )
                assertTrue(
                    "at-risk instant $atRiskInstant must fall before this day ends ($dayEndsAt)",
                    atRiskInstant.isBefore(dayEndsAt),
                )
            }
        }
    }

    @Test
    fun beforeTheAtRiskPointIsNotAtRisk() {
        val dayEndsAt = Instant.parse("2026-08-19T16:00:00Z")
        val justBefore = dayEndsAt.minus(StreakClock.AT_RISK_BEFORE_END).minusSeconds(60)
        assertFalse(StreakClock.isAtRiskWindow(justBefore, dayEndsAt))
    }

    @Test
    fun exactlyAtTheAtRiskPointIsAtRiskInclusive() {
        val dayEndsAt = Instant.parse("2026-08-19T16:00:00Z")
        val atRiskInstant = dayEndsAt.minus(StreakClock.AT_RISK_BEFORE_END)
        assertTrue(StreakClock.isAtRiskWindow(atRiskInstant, dayEndsAt))
    }

    @Test
    fun justBeforeDayEndsIsStillAtRisk() {
        val dayEndsAt = Instant.parse("2026-08-19T16:00:00Z")
        assertTrue(StreakClock.isAtRiskWindow(dayEndsAt.minusSeconds(60), dayEndsAt))
    }

    @Test
    fun nextBoundaryIsTheEarlierOfAtRiskAndDayEnd() {
        val dayEndsAt = Instant.parse("2026-08-19T16:00:00Z")
        val atRiskInstant = dayEndsAt.minus(StreakClock.AT_RISK_BEFORE_END)

        assertEquals(atRiskInstant, StreakClock.nextBoundaryAfter(atRiskInstant.minusSeconds(60), dayEndsAt))
        assertEquals(dayEndsAt, StreakClock.nextBoundaryAfter(atRiskInstant.plusSeconds(60), dayEndsAt))
    }

    @Test
    fun everyReturnedBoundaryIsStrictlyAfterTheInstantPassedIn() {
        val dayEndsAt = Instant.parse("2026-08-19T16:00:00Z")
        val atRiskInstant = dayEndsAt.minus(StreakClock.AT_RISK_BEFORE_END)
        listOf(
            dayEndsAt.minus(Duration.ofHours(8)),
            atRiskInstant.minusSeconds(1),
            atRiskInstant,
            atRiskInstant.plusSeconds(1),
            dayEndsAt.minusSeconds(1),
        ).forEach { now ->
            val boundary = StreakClock.nextBoundaryAfter(now, dayEndsAt)
            assertTrue("$boundary must be strictly after $now", boundary.isAfter(now))
        }
    }
}
