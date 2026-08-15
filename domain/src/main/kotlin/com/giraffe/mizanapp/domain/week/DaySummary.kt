package com.giraffe.mizanapp.domain.week

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DailyScore
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.day.scoreDay
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

/**
 * Builds a [DaySummary] from a plan (stored or derived) and its live
 * completions. Pure, no I/O — the single place this projection is computed,
 * so a derived plan and a stored plan for the same date always summarise
 * identically (FR-020b, `005`/research.md R1).
 */
fun summariseDay(plan: DayPlan, liveCompletions: List<Completion>): DaySummary {
    val score = scoreDay(plan, liveCompletions)

    val tasks = plan.sectionsInOrder().flatMap { (_, sectionTasks) ->
        sectionTasks.map { task ->
            PlannedTaskRecord(task = task, recordedCount = liveCount(liveCompletions, task.taskSlug))
        }
    }

    val state = when {
        score.earned == 0 -> DayCellState.NOTHING_RECORDED
        score.earned >= score.available -> DayCellState.FULLY_RECORDED
        else -> DayCellState.PARTLY_RECORDED
    }

    return DaySummary(
        date = plan.date,
        hijriLabel = plan.hijriLabel,
        score = score,
        state = state,
        tasks = tasks,
    )
}
