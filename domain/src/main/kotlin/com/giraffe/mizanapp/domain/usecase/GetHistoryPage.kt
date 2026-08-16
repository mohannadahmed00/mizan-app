package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.history.HistoryPage
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.repository.CompletionRepository
import com.giraffe.mizanapp.domain.repository.DayPlanRepository
import com.giraffe.mizanapp.domain.repository.RecordCoverageRepository
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.Week
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.domain.week.buildWeekSummary
import com.giraffe.mizanapp.domain.week.projectAvailablePoints
import java.time.LocalDate

/**
 * A page of history: weeks newest-first, floored at the record start.
 *
 * **Writes nothing.** Unlike `GetWeekSummary`, this never calls
 * `ensurePlanFor` - clarification Q2 decided that scrolling must not
 * materialise plans, since a continuous list back to the record start would
 * otherwise turn a scroll into thousands of writes. Unplanned elapsed dates
 * are projected read-only instead (research.md R2, R3).
 */
class GetHistoryPage(
    private val plans: DayPlanRepository,
    private val completions: CompletionRepository,
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val recordCoverage: RecordCoverageRepository,
) {

    suspend operator fun invoke(before: WeekKey? = null, weeksPerPage: Int = 8): HistoryOutcome {
        val recordStart = plans.earliestPlanDate()
        if (recordStart == null) {
            return HistoryOutcome.Ready(HistoryPage(emptyList(), null, hasMore = false, recordStart = null))
        }

        val currentVersion = catalogue.currentVersion()
            ?: return HistoryOutcome.CatalogueUnavailable("no catalogue is available")

        val today = time.today()
        val currentWeek = WeekBoundary.weekContaining(today)
        val recordStartWeek = WeekBoundary.weekContaining(recordStart)

        val requestedNewestStart = before
            ?.let { LocalDate.parse(it.value).minusDays(7) }
            ?: currentWeek.start
        val newestStart = if (requestedNewestStart.isAfter(currentWeek.start)) currentWeek.start else requestedNewestStart

        if (newestStart.isBefore(recordStartWeek.start)) {
            // Nothing left to page - the caller asked for a week older than the record start.
            return HistoryOutcome.Ready(HistoryPage(emptyList(), null, hasMore = false, recordStart = recordStart))
        }

        val pageWeeks = mutableListOf<Week>()
        var cursorStart = newestStart
        while (pageWeeks.size < weeksPerPage) {
            val week = WeekBoundary.weekContaining(cursorStart)
            pageWeeks += week
            if (week.start == recordStartWeek.start) break
            cursorStart = week.start.minusDays(7)
        }

        val spanStart = pageWeeks.last().start
        val spanEnd = pageWeeks.first().end
        val storedPlans = plans.plansBetween(spanStart, spanEnd)
        val liveCompletions = completions.liveBetween(spanStart, spanEnd)
        val plannedDates = storedPlans.map { it.date }.toSet()

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

        val projected = mutableMapOf<LocalDate, Int>()
        for (week in pageWeeks) {
            for (date in week.dates) {
                if (date.isBefore(recordStart)) continue
                if (date in plannedDates) continue
                if (date.isAfter(today)) {
                    val content = catalogueFor(currentVersion) ?: continue
                    projected[date] = projectAvailablePoints(content, currentVersion, date)
                } else {
                    val version = try {
                        versionEffectiveOnCached(date)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    val content = catalogueFor(version) ?: continue
                    projected[date] = projectAvailablePoints(content, version, date)
                }
            }
        }

        val summaries = pageWeeks.map { week ->
            buildWeekSummary(
                week = week,
                today = today,
                recordStart = recordStart,
                plans = storedPlans.filter { !it.date.isBefore(week.start) && !it.date.isAfter(week.end) },
                completions = liveCompletions.filter { !it.creditedDate.isBefore(week.start) && !it.creditedDate.isAfter(week.end) },
                projectedAvailable = projected,
            )
        }

        val oldestWeek = pageWeeks.last()
        val hasMore = oldestWeek.start.isAfter(recordStartWeek.start)

        return HistoryOutcome.Ready(
            HistoryPage(
                weeks = summaries,
                oldestLoaded = oldestWeek.key,
                hasMore = hasMore,
                recordStart = recordStart,
            ),
        )
    }

    // Cached per-date effective version lookup, avoiding repeated suspend calls
    // for dates that share the same catalogue version.
    private val versionCache = mutableMapOf<LocalDate, Int?>()
    private suspend fun versionEffectiveOnCached(date: LocalDate): Int? =
        versionCache.getOrPut(date) { catalogue.versionEffectiveOn(date) }
}

sealed interface HistoryOutcome {
    data class Ready(val page: HistoryPage) : HistoryOutcome
    data class CouldNotLoad(val detail: String) : HistoryOutcome
    data class CatalogueUnavailable(val detail: String) : HistoryOutcome
}
