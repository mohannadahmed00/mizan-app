package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.week.DayCell
import com.giraffe.mizanapp.domain.week.Week

/**
 * The single best day and single best week within the user's recorded
 * history (FR-004). There is deliberately no "worst" field anywhere in this
 * type — Principle IX, spec.md Assumptions.
 */
data class PersonalBests(
    val bestDay: DayCell?,
    val bestWeek: BestWeek?,
    /** True while coverage over the full record is incomplete (FR-023d). */
    val provisional: Boolean = false,
)

/**
 * A bespoke type, not the full [com.giraffe.mizanapp.domain.week.WeekSummary]:
 * a week at either edge of a `recordStart..today` scan can hold fewer than
 * seven [DayCell]s, and `WeekSummary`'s constructor requires exactly seven.
 * [available] sums only the cells actually present for that week — a date
 * before the record start is correctly absent, never zero-padded.
 */
data class BestWeek(val week: Week, val earned: Int, val available: Int) {
    val fraction: Float get() = if (available == 0) 0f else earned.toFloat() / available
}
