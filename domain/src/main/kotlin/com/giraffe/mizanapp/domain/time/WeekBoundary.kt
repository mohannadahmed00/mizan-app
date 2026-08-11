package com.giraffe.mizanapp.domain.time

import com.giraffe.mizanapp.domain.week.Week
import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.LocalDate

/**
 * Where the accountability week begins and ends: Saturday through Friday
 * (FR-001, constitution Principle VII).
 *
 * **This rule exists here and nowhere else.** No screen, query, or aggregate
 * may hold a second opinion about which week a date falls in. Alongside
 * [DayBoundary], this is the complete set of calendar rules in the app.
 */
object WeekBoundary {

    /** The Saturday on or before [date]. */
    fun startOfWeek(date: LocalDate): LocalDate {
        // DayOfWeek.value: MONDAY=1 ... SUNDAY=7.
        // Distance back to the most recent Saturday: SATURDAY(6)->0, SUNDAY(7)->1,
        // MONDAY(1)->2, ..., FRIDAY(5)->6. (value + 1) % 7 yields exactly that.
        val daysSinceSaturday = (date.dayOfWeek.value + 1) % 7
        return date.minusDays(daysSinceSaturday.toLong())
    }

    /** The [Week] that [date] falls in. */
    fun weekContaining(date: LocalDate): Week {
        val start = startOfWeek(date)
        return Week(
            key = WeekKey(start.toString()),
            start = start,
            dates = (0L..6L).map { start.plusDays(it) },
        )
    }
}
