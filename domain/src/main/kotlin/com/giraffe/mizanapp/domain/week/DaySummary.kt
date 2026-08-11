package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.DailyScore
import com.giraffe.mizanapp.domain.day.PlannedTask
import java.time.LocalDate

/** One task on a summarised day: what it was, and how many times it was recorded. */
data class PlannedTaskRecord(
    val task: PlannedTask,
    val recordedCount: Int,
)

/**
 * One date's read-only projection — its plan, its completions, and the
 * resulting [DailyScore]. A projection, not a new record: nothing here is
 * stored, and building one never writes anything.
 *
 * [tasks] preserves the plan's own section-then-position order, so the
 * screen renders sections in the order they applied on that day without
 * re-deriving it.
 */
data class DaySummary(
    val date: LocalDate,
    val hijriLabel: String,
    val score: DailyScore,
    val state: DayCellState,
    val tasks: List<PlannedTaskRecord>,
)
