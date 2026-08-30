package com.giraffe.mizanapp.domain.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A clock tests can move.
 *
 * Every domain test that touches time uses this. No test may read the real
 * clock — a test that does is not testing rollover, it is waiting for it.
 */
class FakeTimeProvider(
    private var instant: Instant = Instant.parse("2026-03-14T12:00:00Z"),
    private var zone: ZoneId = ZoneId.of("Africa/Cairo"),
) : TimeProvider {

    override fun now(): Instant = instant

    override fun today(): LocalDate = DayBoundary.dateAt(instant, zone, null)

    override fun zone(): ZoneId = zone

    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }

    /** Move to a given local date, keeping the time of day. */
    fun setDate(date: LocalDate, time: LocalTime = LocalTime.of(12, 0)) {
        instant = date.atTime(time).atZone(zone).toInstant()
    }

    fun setZone(newZone: ZoneId) {
        zone = newZone
    }
}
