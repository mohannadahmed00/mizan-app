package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where the accountability day begins and ends: local midnight to local
 * midnight (FR-030, constitution Principle VII).
 *
 * **This rule exists here and nowhere else.** Two screens must never be able to
 * disagree about which day an instant belongs to, so no other code may convert
 * an instant to an accountability date.
 *
 * The Hijri date is a label attached to a day, never the thing that defines the
 * day's boundaries — see `HijriLabel`.
 */
object DayBoundary {

    fun dateAt(instant: Instant, zone: ZoneId): LocalDate =
        instant.atZone(zone).toLocalDate()
}
