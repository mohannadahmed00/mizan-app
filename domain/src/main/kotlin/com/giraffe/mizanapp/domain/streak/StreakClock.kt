package com.giraffe.mizanapp.domain.streak

import java.time.Duration
import java.time.Instant

/**
 * The single home of the at-risk rule (Principle VII).
 *
 * Reads no clock itself, and no longer reads a zone either: a fixed 20:00 local assumed a
 * midnight-length day, which a Maghrib-anchored one no longer is (FR-029). [isAtRiskWindow] and
 * [nextBoundaryAfter] instead take [dayEndsAt] — the boundary's own `expiresAt` — so the window is
 * always measured backward from when *this* day actually ends, whatever its length. The duration
 * is a constant rather than a setting, because settings are outside the MVP.
 */
object StreakClock {

    val AT_RISK_BEFORE_END: Duration = Duration.ofHours(4)

    fun isAtRiskWindow(now: Instant, dayEndsAt: Instant): Boolean =
        !now.isBefore(dayEndsAt.minus(AT_RISK_BEFORE_END))

    /** The earlier of the at-risk instant and [dayEndsAt], whichever is strictly after [now]. */
    fun nextBoundaryAfter(now: Instant, dayEndsAt: Instant): Instant {
        val atRiskInstant = dayEndsAt.minus(AT_RISK_BEFORE_END)
        return if (now.isBefore(atRiskInstant)) atRiskInstant else dayEndsAt
    }
}
