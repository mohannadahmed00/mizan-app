package com.giraffe.mizanapp.weeklysummary

import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.LocalDate

/**
 * The Weekly Summary screen's one immutable state (contracts/ui-state.md §2).
 *
 * There is deliberately no "current week" variant — the week in progress
 * belongs to the weekly sheet, never here (FR-027a).
 */
data class WeeklySummaryUiState(
    val content: WeeklySummaryContent,
    val canGoEarlier: Boolean,
    val canGoLater: Boolean,
)

sealed interface WeeklySummaryContent {
    /** No week has closed yet (FR-027b). */
    data class Waiting(val firstSummaryAt: LocalDate) : WeeklySummaryContent

    /**
     * No field here counts anything not done, compares to another person, or
     * is framed as a target fallen short of (FR-025, Principle IX).
     * [pointsEarned] against [pointsAvailable] is a ratio, never a deficit.
     */
    data class Closed(
        val weekKey: WeekKey,
        val range: String,
        val daysEngaged: Int,
        val daysInWeek: Int,
        val tasksRecorded: Int,
        val pointsEarned: Int,
        val pointsAvailable: Int,
        val streakAtClose: Int,
        val coverage: CoverageNote?,
        val quiet: Boolean,
    ) : WeeklySummaryContent

    data class Unavailable(val reason: String) : WeeklySummaryContent
}

/** A partly-recorded week, described as coverage, never as a shortfall (FR-029). */
data class CoverageNote(val coveredFrom: LocalDate)
