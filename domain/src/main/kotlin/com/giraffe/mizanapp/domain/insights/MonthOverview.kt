package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.week.DayCell
import java.time.YearMonth

/**
 * What the monthly overview renders: every calendar date in [month] as a
 * [DayCell] — the same type [com.giraffe.mizanapp.domain.week.WeekSummary]
 * uses (research.md R3), so the Q1 clarification's four bands need no second
 * type or color table.
 */
data class MonthOverview(
    val month: YearMonth,
    val days: List<DayCell>,
)
