package com.giraffe.mizanapp.domain.insights

import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.DayCell
import com.giraffe.mizanapp.domain.week.DayCellState
import java.time.LocalDate

/**
 * Scans a full-record [DayCell] payload for the single highest-percentage
 * day and single highest-percentage week (FR-004). Pure, no I/O — the
 * grouping into weeks happens locally over the cells already passed in, no
 * further repository calls (research.md "Full-record scan bound").
 *
 * Cells in [DayCellState.OUTSIDE_RECORD] or [DayCellState.NOT_YET_ELAPSED]
 * never count toward either best — a day that never happened cannot be a
 * personal best, and cannot inflate a week's denominator either.
 *
 * Ties resolve to the earliest occurrence, matching how a user expects "my
 * best day" to read for a repeated 100%.
 */
fun buildPersonalBests(dayCells: List<DayCell>, today: LocalDate): PersonalBests {
    val eligible = dayCells.filter { it.state != DayCellState.OUTSIDE_RECORD && it.state != DayCellState.NOT_YET_ELAPSED }

    val bestDay = eligible
        .filter { it.available > 0 }
        .maxWithOrNull(compareBy<DayCell> { it.earned.toFloat() / it.available }.thenComparing { c -> c.date.toEpochDay() * -1 })

    val bestWeek = eligible
        .groupBy { WeekBoundary.weekContaining(it.date).key }
        .mapNotNull { (_, cells) ->
            val week = WeekBoundary.weekContaining(cells.first().date)
            val earned = cells.sumOf { it.earned }
            val available = cells.sumOf { it.available }
            if (available == 0) null else BestWeek(week, earned, available)
        }
        .maxWithOrNull(compareBy<BestWeek> { it.fraction }.thenComparing { w -> w.week.start.toEpochDay() * -1 })

    return PersonalBests(bestDay = bestDay, bestWeek = bestWeek)
}
