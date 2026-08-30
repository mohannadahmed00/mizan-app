package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where the accountability day begins and ends: calculated Maghrib to the next
 * calculated Maghrib, with local midnight as the explicit fallback (FR-030,
 * Constitution Principle VII).
 *
 * **This rule exists here and nowhere else.** Two screens must never be able to
 * disagree about which day an instant belongs to, so no other code may convert
 * an instant to an accountability date.
 *
 * The Hijri date is a label attached to a day, never the thing that defines the
 * day's boundaries — see `HijriLabel`.
 */
object DayBoundary {

    fun dateAt(
        instant: Instant,
        zone: ZoneId,
        maghribOnCivilDate: Instant?,
    ): LocalDate {
        val civilDate = instant.atZone(zone).toLocalDate()
        if (maghribOnCivilDate == null) return civilDate
        return if (!instant.isBefore(maghribOnCivilDate)) civilDate.plusDays(1) else civilDate
    }
}
