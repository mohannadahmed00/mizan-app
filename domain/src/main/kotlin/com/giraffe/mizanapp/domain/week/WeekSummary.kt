package com.giraffe.mizanapp.domain.week

import java.time.LocalDate

/** One date's figures as shown on the weekly sheet. */
data class DayCell(
    val date: LocalDate,
    val hijriLabel: String?,
    val earned: Int,
    val available: Int,
    val state: DayCellState,
)

/** What the weekly sheet renders: a [Week], its [WeeklyScore], and seven [DayCell]s. */
data class WeekSummary(
    val week: Week,
    val score: WeeklyScore,
    val days: List<DayCell>,
) {
    init {
        require(days.size == 7) { "a week summary must contain exactly seven day cells, got ${days.size}" }
    }
}
