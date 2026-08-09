package com.giraffe.mizanapp.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only source of the current time in this application (Principle VII).
 *
 * No other code may call `Instant.now()`, `LocalDate.now()`,
 * `System.currentTimeMillis()` or `ZoneId.systemDefault()`. Day rollover and
 * streak logic are untestable without this seam, and every hard bug in an app
 * like this is a date bug.
 */
interface TimeProvider {

    fun now(): Instant

    /** The current accountability date, per [DayBoundary]. */
    fun today(): LocalDate

    fun zone(): ZoneId
}
