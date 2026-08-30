package com.giraffe.mizanapp.data.time

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
 *
 * `today()` reads [boundaryStateStore]'s already-resolved state rather than converting an
 * instant to a date itself — that conversion is `DayBoundary.dateAt`'s alone, and this class
 * calls only `current()`, never `refresh()`, so as not to re-enter the cycle rule 5 in the
 * tasks exists to prevent.
 */
class SystemTimeProvider(private val boundaryStateStore: BoundaryStateStore) : TimeProvider {

    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun today(): LocalDate = boundaryStateStore.current().resolvedDate
}
