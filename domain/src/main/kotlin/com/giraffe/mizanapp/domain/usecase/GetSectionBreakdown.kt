package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.history.deriveDayPlan
import com.giraffe.mizanapp.domain.insights.InsightsPeriod
import com.giraffe.mizanapp.domain.insights.SectionPerformance
import com.giraffe.mizanapp.domain.insights.buildSectionBreakdown
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate

/**
 * A section-by-section breakdown, always for the *current* week or month —
 * never a past period (spec.md Assumptions; `InsightsPeriod`'s own doc).
 *
 * Restricted to elapsed dates (`<= today`) before reading, mirroring
 * `WeeklyScore.elapsedAvailable`'s own convention: a future date's projected
 * points never inflate a section's `available` (research.md R4). **Never
 * calls `ensurePlanFor`** — an unstored elapsed date is derived read-only
 * via `deriveDayPlan` (`005`), exactly like `GetHistoryPage`.
 */
class GetSectionBreakdown(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val recordCoverage: RecordCoverageRepository,
) {
    suspend operator fun invoke(period: InsightsPeriod): SectionBreakdownOutcome {
        catalogue.currentVersion() ?: return SectionBreakdownOutcome.CatalogueUnavailable("no catalogue is available")

        val coverage = recordCoverage.coverage()
        val today = time.today()
        val (rangeStart, rangeEnd) = when (period) {
            is InsightsPeriod.ForWeek -> period.week.start to period.week.end
            is InsightsPeriod.ForMonth -> period.month.atDay(1) to period.month.atEndOfMonth()
        }
        val elapsedEnd = if (rangeEnd.isAfter(today)) today else rangeEnd
        if (rangeStart.isAfter(elapsedEnd)) return SectionBreakdownOutcome.Ready(emptyList())

        val storedPlans = plans.plansBetween(rangeStart, elapsedEnd)
        val liveCompletions = completions.liveBetween(rangeStart, elapsedEnd)
        val plannedDates = storedPlans.map { it.date }.toSet()

        val dates = generateSequence(rangeStart) { it.plusDays(1) }.takeWhile { !it.isAfter(elapsedEnd) }.toList()
        val derived = mutableListOf<DayPlan>()
        for (date in dates) {
            if (date in plannedDates) continue
            if (!coverage.isKnown(date)) continue
            val version = try {
                catalogue.versionEffectiveOn(date)
            } catch (e: Exception) {
                null
            } ?: continue
            val content = try {
                catalogue.catalogueAt(version)
            } catch (e: Exception) {
                null
            } ?: continue
            derived += deriveDayPlan(content, version, date)
        }

        val sections: List<SectionPerformance> = buildSectionBreakdown(storedPlans + derived, liveCompletions)
        return SectionBreakdownOutcome.Ready(sections, provisional = !coverage.complete)
    }
}

sealed interface SectionBreakdownOutcome {
    /** [provisional] is true while coverage over the requested period is incomplete (FR-023d). */
    data class Ready(val sections: List<SectionPerformance>, val provisional: Boolean = false) : SectionBreakdownOutcome
    data class CatalogueUnavailable(val detail: String) : SectionBreakdownOutcome
}
