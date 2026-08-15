package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.history.deriveDayPlan
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.week.DaySummary
import com.giraffe.mizanapp.domain.week.summariseDay
import java.time.LocalDate

/**
 * A past date's detail — materialising its plan best-effort if it does not
 * yet exist, never gating the read on that write (FR-020c, research.md R4).
 *
 * Only ever called for an **elapsed** date — the current date is routed to
 * the recording surface instead (FR-015a). Still refuses a future date rather
 * than assuming callers behave.
 */
class GetDayDetail(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(date: LocalDate): DayDetailOutcome {
        val today = time.today()
        val recordStart = plans.earliestPlanDate()

        if (date.isAfter(today) || recordStart == null || date.isBefore(recordStart)) {
            return DayDetailOutcome.NoRecord
        }

        plans.planFor(date)?.let { plan ->
            return DayDetailOutcome.Ready(summariseDay(plan, completions.liveBetween(date, date)))
        }

        // Best-effort store. Any failure is swallowed - the figures below come
        // from a fresh read (if it succeeded) or from derivation (if it
        // didn't), never from trusting this call's return value (FR-020c).
        try {
            plans.ensurePlanFor(date)
        } catch (e: Exception) {
            // Deliberately swallowed - a failed write must never cost the user the view.
        }

        plans.planFor(date)?.let { plan ->
            return DayDetailOutcome.Ready(summariseDay(plan, completions.liveBetween(date, date)))
        }

        val version = catalogue.versionEffectiveOn(date)
            ?: return DayDetailOutcome.CatalogueUnavailable("no catalogue version applies to $date")
        val content = catalogue.catalogueAt(version)
            ?: return DayDetailOutcome.CatalogueUnavailable("catalogue version $version is unavailable")

        val derived = deriveDayPlan(content, version, date)
        return DayDetailOutcome.Ready(summariseDay(derived, completions.liveBetween(date, date)))
    }
}

sealed interface DayDetailOutcome {
    data class Ready(val summary: DaySummary) : DayDetailOutcome
    data object NoRecord : DayDetailOutcome
    data class CatalogueUnavailable(val detail: String) : DayDetailOutcome
}
