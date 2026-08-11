package com.giraffe.mizanapp.domain.week

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A stable identity for a week, derived directly from its Saturday. Not an
 * ISO week number — those are Monday-first and would need a second rule to
 * translate into this app's Saturday-to-Friday week (Principle VII allows
 * exactly one week rule).
 */
@JvmInline
value class WeekKey(val value: String)

/**
 * Seven consecutive dates, Saturday through Friday.
 */
data class Week(
    val key: WeekKey,
    val start: LocalDate,
    val dates: List<LocalDate>,
) {
    init {
        require(dates.size == 7) { "a week must contain exactly seven dates, got ${dates.size}" }
        require(dates.first() == start) { "the first date must equal start" }
        require(start.dayOfWeek == DayOfWeek.SATURDAY) { "a week must start on a Saturday, got ${start.dayOfWeek}" }
    }

    val end: LocalDate get() = dates.last()
}
