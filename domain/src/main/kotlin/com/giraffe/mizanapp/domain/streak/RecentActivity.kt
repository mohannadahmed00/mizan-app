package com.giraffe.mizanapp.domain.streak

import java.time.LocalDate

/**
 * Four states, never two.
 *
 * Collapsing [TODAY_PENDING] into [NOT_RECORDED] shows a day the user has
 * not had yet as a day they missed. Collapsing [OUTSIDE_RECORD] into it
 * opens a fresh install on six failures. Both are Principle IX violations
 * that a two-state model makes unavoidable, which is why the states live
 * here and not in the composable. None of the four is a failure state, and
 * none may acquire a colour or glyph that reads as one.
 */
enum class ActivityState { COUNTED, NOT_RECORDED, TODAY_PENDING, OUTSIDE_RECORD }

data class ActivityDay(val date: LocalDate, val state: ActivityState)

/** The seven most recent dates up to and including [today], oldest first. */
fun buildRecentActivity(
    consistencyDates: Set<LocalDate>,
    today: LocalDate,
    recordStart: LocalDate?,
): List<ActivityDay> = (6 downTo 0).map { offset ->
    val date = today.minusDays(offset.toLong())
    val state = when {
        date in consistencyDates -> ActivityState.COUNTED
        date == today -> ActivityState.TODAY_PENDING
        recordStart == null || date.isBefore(recordStart) -> ActivityState.OUTSIDE_RECORD
        else -> ActivityState.NOT_RECORDED
    }
    ActivityDay(date, state)
}
