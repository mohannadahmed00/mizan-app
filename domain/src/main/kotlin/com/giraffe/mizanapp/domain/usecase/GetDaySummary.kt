package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.DaySummary
import com.giraffe.mizanapp.domain.week.PlannedTaskRecord
import java.time.LocalDate

/**
 * One date's read-only summary. Built entirely from that date's **stored
 * plan** and its completions — the live catalogue is never consulted, so a
 * catalogue change cannot alter what a past day reports (FR-023,
 * Principle III).
 *
 * Read-only by construction: there is no write path here, and it never
 * creates a plan — backfill is `GetWeekSummary`'s job and happens before a
 * day is reachable through the sheet.
 */
class GetDaySummary(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
) {
    suspend operator fun invoke(date: LocalDate): DaySummary? {
        val plan = plans.planFor(date) ?: return null
        val liveCompletions = completions.liveBetween(date, date)
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
}
