package com.giraffe.mizanapp.week

import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.LocalDate

/**
 * One immutable state for the Week screen, exposed as [kotlinx.coroutines.flow.StateFlow].
 *
 * [progressFraction] divides by [elapsedAvailablePoints], never by
 * [weekTargetPoints] — that single choice is what stops a Sunday morning
 * reading as 10% of a week (FR-009a).
 */
data class WeekUiState(
    val status: Status = Status.Loading,
    val weekKey: WeekKey? = null,
    val startDate: LocalDate? = null,
    val days: List<DayCellUi> = emptyList(),
    val earnedPoints: Int = 0,
    val elapsedAvailablePoints: Int = 0,
    val weekTargetPoints: Int = 0,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
) {
    val progressFraction: Float
        get() = if (elapsedAvailablePoints == 0) 0f else earnedPoints.toFloat() / elapsedAvailablePoints

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data class CouldNotLoad(val detail: String) : Status
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

data class DayCellUi(
    val date: LocalDate,
    val dayLabel: String,
    val hijriLabel: String?,
    val earnedPoints: Int,
    val availablePoints: Int,
    val state: DayCellState,
) {
    val isOpenable: Boolean
        get() = state != DayCellState.OUTSIDE_RECORD && state != DayCellState.NOT_YET_ELAPSED
}

/**
 * Everything the user can do on this screen. There is deliberately no event
 * for completing, undoing, adding, removing, reordering or repricing
 * anything — Principle VI made structural, exactly as `002` did for
 * `TodayEvent`.
 */
sealed interface WeekEvent {
    data object PreviousWeek : WeekEvent
    data object NextWeek : WeekEvent
    data class OpenDay(val date: LocalDate) : WeekEvent
    data object Retry : WeekEvent

    /** Opens history (`005`, FR-001). Handled by the host navigating, like [OpenDay]. */
    data object OpenHistory : WeekEvent

    /** Opens Insights (`006`). Handled by the host navigating, like [OpenHistory]. */
    data object OpenInsights : WeekEvent

    /** Opens the Weekly Summary screen (`010`). Handled by the host navigating, like [OpenInsights]. */
    data object OpenWeeklySummary : WeekEvent
}
