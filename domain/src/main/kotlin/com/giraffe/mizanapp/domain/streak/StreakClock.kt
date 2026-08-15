package com.giraffe.mizanapp.domain.streak

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The single home of the 20:00 rule (Principle VII).
 *
 * Reads no clock itself — the instant and zone are always passed in, exactly
 * as `DayBoundary.dateAt` takes them. The threshold is a constant rather than
 * a setting, because settings are outside the MVP.
 */
object StreakClock {

    val AT_RISK_FROM: LocalTime = LocalTime.of(20, 0)

    fun isAtRiskWindow(now: Instant, zone: ZoneId): Boolean {
        val localTime = now.atZone(zone).toLocalTime()
        return !localTime.isBefore(AT_RISK_FROM)
    }

    /** The next of today's 20:00 and tomorrow's midnight, strictly after [now]. */
    fun nextBoundaryAfter(now: Instant, zone: ZoneId): Instant {
        val zoned = now.atZone(zone)
        val todayAtRisk = zoned.toLocalDate().atTime(AT_RISK_FROM).atZone(zone).toInstant()
        return if (now.isBefore(todayAtRisk)) {
            todayAtRisk
        } else {
            zoned.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        }
    }
}
