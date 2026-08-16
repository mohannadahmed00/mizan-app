package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.week.buildDayCells
import java.time.LocalDate
import java.time.YearMonth

/**
 * The pure aggregate over a calendar month's stored plans and completions.
 *
 * Reuses [buildDayCells] (research.md R2) — the same five-state derivation
 * `buildWeekSummary` uses, applied to a month's dates instead of a week's
 * seven. No repositories, no clock, no I/O.
 */
fun buildMonthOverview(
    month: YearMonth,
    today: LocalDate,
    recordStart: LocalDate?,
    plans: List<DayPlan>,
    completions: List<Completion>,
    projectedAvailable: Map<LocalDate, Int>,
    coverage: RecordCoverage = RecordCoverage.completeFrom(recordStart),
): MonthOverview {
    val dates = generateSequence(month.atDay(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(month.atEndOfMonth()) }.toList()
    val days = buildDayCells(dates, today, recordStart, plans, completions, projectedAvailable, coverage)
    return MonthOverview(month = month, days = days)
}
