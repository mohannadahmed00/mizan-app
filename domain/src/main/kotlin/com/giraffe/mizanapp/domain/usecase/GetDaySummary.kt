package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.week.DaySummary
import com.giraffe.mizanapp.domain.week.summariseDay
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
        return summariseDay(plan, liveCompletions)
    }
}
