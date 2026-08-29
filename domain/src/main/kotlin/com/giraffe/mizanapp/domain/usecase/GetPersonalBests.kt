package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.insights.PersonalBests
import com.giraffe.mizanapp.domain.insights.buildPersonalBests
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.week.buildDayCells
import com.giraffe.mizanapp.domain.week.projectAvailablePoints
import java.time.LocalDate

/**
 * The one full-record read in this feature (FR-004): the whole
 * `recordStart..today` span, once, never paginated. **Never calls
 * `ensurePlanFor`** — an unstored elapsed date is projected read-only,
 * exactly like `GetHistoryPage`'s own resilience (research.md "Full-record
 * scan bound for GetPersonalBests").
 */
class GetPersonalBests(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val recordCoverage: RecordCoverageRepository,
) {
    suspend operator fun invoke(): PersonalBestsOutcome {
        val recordStart = plans.earliestPlanDate() ?: return PersonalBestsOutcome.NoHistory
        catalogue.currentVersion() ?: return PersonalBestsOutcome.CatalogueUnavailable("no catalogue is available")

        val coverage = recordCoverage.coverage()
        val today = time.today()
        val storedPlans = plans.plansBetween(recordStart, today)
        val liveCompletions = completions.liveBetween(recordStart, today)
        val plannedDates = storedPlans.map { it.date }.toSet()
        val dates = generateSequence(recordStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()

        val catalogueCache = mutableMapOf<Int, Catalogue?>()
        suspend fun catalogueFor(version: Int): Catalogue? {
            if (catalogueCache.containsKey(version)) return catalogueCache[version]
            val content = try {
                catalogue.catalogueAt(version)
            } catch (e: Exception) {
                null
            }
            catalogueCache[version] = content
            return content
        }

        val versionCache = mutableMapOf<LocalDate, Int?>()
        suspend fun versionEffectiveOnCached(date: LocalDate): Int? =
            versionCache.getOrPut(date) {
                try {
                    catalogue.versionEffectiveOn(date)
                } catch (e: Exception) {
                    null
                }
            }

        val projected = mutableMapOf<LocalDate, Int>()
        for (date in dates) {
            if (date in plannedDates) continue
            val version = versionEffectiveOnCached(date) ?: continue
            val content = catalogueFor(version) ?: continue
            projected[date] = projectAvailablePoints(content, version, date)
        }

        val cells = buildDayCells(dates, today, recordStart, storedPlans, liveCompletions, projected, coverage)
        return PersonalBestsOutcome.Ready(buildPersonalBests(cells, today).copy(provisional = !coverage.complete))
    }
}

sealed interface PersonalBestsOutcome {
    data class Ready(val bests: PersonalBests) : PersonalBestsOutcome
    data object NoHistory : PersonalBestsOutcome
    data class CatalogueUnavailable(val detail: String) : PersonalBestsOutcome
}
