package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.time.HijriLabel
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.Week
import com.giraffe.mizanapp.domain.week.WeekSummary
import com.giraffe.mizanapp.domain.week.buildWeekSummary
import com.giraffe.mizanapp.domain.week.projectAvailablePoints
import java.time.LocalDate

/**
 * Displays one already-closed week, without writing anything.
 *
 * Unlike [GetWeekSummary], this never calls `ensurePlanFor` — a background
 * notification path must never write history (Principle III, research R4). A
 * date inside the week with no stored plan is projected with the catalogue
 * version that was actually effective on it, never the current one, so a
 * later catalogue change cannot move a closed week's figures (FR-024).
 */
class GetClosedWeekSummary(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val recordCoverage: RecordCoverageRepository,
) {
    suspend operator fun invoke(week: Week): ClosedWeekOutcome {
        val recordStart = plans.earliestPlanDate()
        val coverage = recordCoverage.coverage()
        val storedPlans = plans.plansBetween(week.start, week.end)
        val storedDates = storedPlans.map { it.date }.toSet()
        val liveCompletions = completions.liveBetween(week.start, week.end)

        val missingDates = week.dates.filter { it !in storedDates }
        val projected = mutableMapOf<LocalDate, Int>()
        for (date in missingDates) {
            if (!coverage.isKnown(date)) continue
            if (recordStart != null && date.isBefore(recordStart)) continue
            val version = catalogue.versionEffectiveOn(date) ?: return ClosedWeekOutcome.NoCatalogue(week)
            val content = catalogue.catalogueAt(version) ?: return ClosedWeekOutcome.NoCatalogue(week)
            projected[date] = projectAvailablePoints(content, version, date)
        }

        val summary = buildWeekSummary(
            week = week,
            today = week.end,
            recordStart = recordStart,
            plans = storedPlans,
            completions = liveCompletions,
            projectedAvailable = projected,
            coverage = coverage,
        )
        // A projected day (no stored plan) carries no hijriLabel from buildDayCells, unlike a
        // backfilled one from GetWeekSummary — SC-006 requires the two paths to agree, so the
        // label is filled in here rather than by touching the shared day-cell derivation.
        val labelled = summary.copy(
            days = summary.days.map { cell ->
                if (cell.hijriLabel == null && cell.state in LABELLED_STATES) {
                    cell.copy(hijriLabel = HijriLabel.forDate(cell.date))
                } else {
                    cell
                }
            },
        )
        return ClosedWeekOutcome.Ready(labelled)
    }

    private companion object {
        val LABELLED_STATES = setOf(DayCellState.NOTHING_RECORDED, DayCellState.PARTLY_RECORDED, DayCellState.FULLY_RECORDED)
    }
}

sealed interface ClosedWeekOutcome {
    data class Ready(val summary: WeekSummary) : ClosedWeekOutcome
    data class NoCatalogue(val week: Week) : ClosedWeekOutcome
}
