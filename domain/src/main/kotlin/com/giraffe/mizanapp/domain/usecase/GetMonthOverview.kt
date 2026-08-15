package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.insights.MonthOverview
import com.giraffe.mizanapp.domain.insights.buildMonthOverview
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.week.projectAvailablePoints
import java.time.LocalDate
import java.time.YearMonth

/**
 * A calendar month, read-only. **Never calls `ensurePlanFor`** — paging
 * months must cost zero writes, exactly like scrolling history (`005`).
 *
 * Elapsed unplanned dates are projected with `versionEffectiveOn(date)`, not
 * `currentVersion()` — the mid-period catalogue-change guarantee (FR-006,
 * SC-003). A date this can't resolve is skipped rather than failing the
 * whole read, mirroring `GetHistoryPage`'s own resilience (research.md R4).
 */
class GetMonthOverview(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(month: YearMonth): MonthOverviewOutcome {
        val currentVersion = catalogue.currentVersion()
            ?: return MonthOverviewOutcome.CatalogueUnavailable("no catalogue is available")

        val today = time.today()
        val recordStart = plans.earliestPlanDate()
        val start = month.atDay(1)
        val end = month.atEndOfMonth()

        val storedPlans = plans.plansBetween(start, end)
        val liveCompletions = completions.liveBetween(start, end)
        val plannedDates = storedPlans.map { it.date }.toSet()
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

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
            if (recordStart == null || date.isBefore(recordStart)) continue

            if (date.isAfter(today)) {
                val content = catalogueFor(currentVersion) ?: continue
                projected[date] = projectAvailablePoints(content, currentVersion, date)
            } else {
                val version = versionEffectiveOnCached(date) ?: continue
                val content = catalogueFor(version) ?: continue
                projected[date] = projectAvailablePoints(content, version, date)
            }
        }

        val overview = buildMonthOverview(month, today, recordStart, storedPlans, liveCompletions, projected)
        return MonthOverviewOutcome.Ready(overview)
    }
}

sealed interface MonthOverviewOutcome {
    data class Ready(val overview: MonthOverview) : MonthOverviewOutcome
    data class CatalogueUnavailable(val detail: String) : MonthOverviewOutcome
}
