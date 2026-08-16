package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.week.Week
import com.giraffe.mizanapp.domain.week.WeekSummary
import com.giraffe.mizanapp.domain.week.buildWeekSummary
import com.giraffe.mizanapp.domain.week.projectAvailablePoints

/**
 * Displays one week: backfills its elapsed unopened days, then aggregates.
 *
 * The only orchestrator in this feature — it is the single place the "display
 * a week" rule lives, so neither a ViewModel nor a repository holds it, and no
 * second screen can implement it differently.
 */
class GetWeekSummary(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val recordCoverage: RecordCoverageRepository,
) {
    suspend operator fun invoke(week: Week): WeekOutcome {
        val currentVersion = catalogue.currentVersion() ?: return WeekOutcome.NoCatalogue(week)
        val today = time.today()

        // The floor is a snapshot taken before this invocation writes
        // anything — it must never be affected by the backfill this same
        // call performs (FR-012).
        val recordStart = plans.earliestPlanDate()
        if (recordStart != null) {
            val toBackfill = week.dates.filter { it.isBefore(today) && !it.isBefore(recordStart) }
            for (date in toBackfill) {
                if (plans.planFor(date) != null) continue
                val backfilled = try {
                    plans.ensurePlanFor(date)
                } catch (e: Exception) {
                    return WeekOutcome.BackfillFailed(week)
                }
                if (backfilled is EnsureOutcome.NoCatalogue) return WeekOutcome.BackfillFailed(week)
            }
        }

        val storedPlans = plans.plansBetween(week.start, week.end)
        val liveCompletions = completions.liveBetween(week.start, week.end)

        val futureDates = week.dates.filter { it.isAfter(today) }
        val projected = if (futureDates.isEmpty()) {
            emptyMap()
        } else {
            val content = catalogue.catalogueAt(currentVersion) ?: return WeekOutcome.NoCatalogue(week)
            futureDates.associateWith { projectAvailablePoints(content, currentVersion, it) }
        }

        val summary = buildWeekSummary(
            week = week,
            today = today,
            recordStart = recordStart,
            plans = storedPlans,
            completions = liveCompletions,
            projectedAvailable = projected,
        )
        return WeekOutcome.Ready(summary)
    }
}

sealed interface WeekOutcome {
    data class Ready(val summary: WeekSummary) : WeekOutcome
    data class BackfillFailed(val week: Week) : WeekOutcome
    data class NoCatalogue(val week: Week) : WeekOutcome
}
