package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.domain.week.WeekSummary

/**
 * The weekly trend chart's data: `GetHistoryPage`, reordered — not a second
 * week-aggregation implementation (research.md R1).
 *
 * The initial call (`before = null`) loads the most recent [weeks] weeks. To
 * scroll further back (US1 Acceptance Scenario 3), the caller passes the
 * `WeekKey` of the oldest week currently loaded as `before` and prepends the
 * result to what it already has, mirroring `HistoryEvent.LoadMore`'s
 * load-more-and-prepend shape (`005`).
 */
class GetWeeklyTrend(private val historyPage: GetHistoryPage) {

    suspend operator fun invoke(before: WeekKey? = null, weeks: Int = 8): TrendOutcome {
        return when (val outcome = historyPage(before = before, weeksPerPage = weeks)) {
            is HistoryOutcome.Ready -> {
                val page = outcome.page
                if (page.weeks.isEmpty() && before == null) {
                    TrendOutcome.NoHistory
                } else {
                    TrendOutcome.Ready(weeks = page.weeks.reversed(), hasMore = page.hasMore)
                }
            }
            is HistoryOutcome.CouldNotLoad -> TrendOutcome.CouldNotLoad(outcome.detail)
            is HistoryOutcome.CatalogueUnavailable -> TrendOutcome.CatalogueUnavailable(outcome.detail)
        }
    }
}

sealed interface TrendOutcome {
    /** [weeks] is oldest-first — charts read left-to-right as time moving forward. */
    data class Ready(val weeks: List<WeekSummary>, val hasMore: Boolean) : TrendOutcome
    data object NoHistory : TrendOutcome
    data class CouldNotLoad(val detail: String) : TrendOutcome
    data class CatalogueUnavailable(val detail: String) : TrendOutcome
}
