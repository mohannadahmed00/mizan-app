package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import java.time.LocalDate

/**
 * The pure aggregate over a week's stored plans and completions.
 *
 * No repositories, no clock, no I/O — everything it needs is passed in. This
 * is what makes the whole of FR-009's two-denominator rule testable in
 * milliseconds, and it is the function `GetWeekSummary` calls after backfill
 * has finished (or, for a week that needs none, without touching storage at
 * all).
 *
 * [projectedAvailable] MUST hold an entry for every date in [week] with no
 * stored [plan] that is at or after [recordStart] — future dates (FR-009d)
 * and, since `005`, elapsed dates history never backfilled (research.md R2).
 * A stored plan's own `availablePoints` always wins over the projection,
 * which is what stops a catalogue change from ever moving a recorded day
 * (Principle III).
 */
fun buildWeekSummary(
    week: Week,
    today: LocalDate,
    recordStart: LocalDate?,
    plans: List<DayPlan>,
    completions: List<Completion>,
    projectedAvailable: Map<LocalDate, Int>,
): WeekSummary {
    val plansByDate = plans.associateBy { it.date }
    val completionsByDate = completions.filter { it.isLive }.groupBy { it.creditedDate }

    val days = week.dates.map { date ->
        val plan = plansByDate[date]
        val earned = completionsByDate[date].orEmpty().sumOf { it.pointsAwarded }

        val state = when {
            date.isAfter(today) -> DayCellState.NOT_YET_ELAPSED
            recordStart == null || date.isBefore(recordStart) -> DayCellState.OUTSIDE_RECORD
            plan == null -> DayCellState.NOTHING_RECORDED
            earned == 0 -> DayCellState.NOTHING_RECORDED
            earned >= plan.availablePoints -> DayCellState.FULLY_RECORDED
            else -> DayCellState.PARTLY_RECORDED
        }

        val available = when {
            state == DayCellState.OUTSIDE_RECORD -> 0
            plan != null -> plan.availablePoints
            else -> projectedAvailable[date] ?: 0
        }

        DayCell(
            date = date,
            hijriLabel = plan?.hijriLabel,
            earned = earned,
            available = available,
            state = state,
        )
    }

    val elapsedAvailable = days.filter { !it.date.isAfter(today) }.sumOf { it.available }
    val futureAvailable = days.filter { it.date.isAfter(today) }.sumOf { it.available }
    val weekTarget = elapsedAvailable + futureAvailable
    val earned = days.sumOf { it.earned }

    return WeekSummary(
        week = week,
        score = WeeklyScore(earned = earned, elapsedAvailable = elapsedAvailable, weekTarget = weekTarget),
        days = days,
    )
}
