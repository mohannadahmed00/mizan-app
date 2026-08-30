package com.giraffe.mizanapp.data.time

import com.giraffe.mizanapp.domain.time.DayBoundary
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one place in this project permitted to read the real clock.
 *
 * Constitution Principle VII: no code outside a single injected time provider
 * may read the system clock, current date, or default timezone. If you find
 * `Instant.now()` or `ZoneId.systemDefault()` anywhere else, that is a bug.
 */
class SystemTimeProvider : TimeProvider {

    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun today(): LocalDate = DayBoundary.dateAt(now(), zone(), null)
}
