package com.giraffe.mizanapp.history

import com.giraffe.mizanapp.week.DayCellUi
import java.time.LocalDate
import com.giraffe.mizanapp.domain.week.WeekKey

/**
 * One immutable state for the History screen, exposed as
 * [kotlinx.coroutines.flow.StateFlow].
 *
 * [isLoadingMore] is deliberately distinct from [Status.Loading] — appending a
 * page must never blank the list or move the user's scroll position (FR-005).
 */
data class HistoryUiState(
    val status: Status = Status.Loading,
    val weeks: List<WeekRowUi> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
) {
    sealed interface Status {
        data object Loading : Status
        data object Ready : Status

        /** No plan exists anywhere - a beginning, not an empty list (FR-007). */
        data object RecordNotStarted : Status
        data class CouldNotLoad(val detail: String) : Status

        /** Weeks already loaded still render; this only names what cannot be built (FR-032). */
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

data class WeekRowUi(
    val weekKey: WeekKey,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val earnedPoints: Int,
    val availablePoints: Int,
    val days: List<DayCellUi>,
)

/**
 * There is deliberately no event here for completing, undoing, adding,
 * removing, reordering or repricing anything — Principle VI made structural,
 * exactly as `002` did for `TodayEvent` and `003` for `WeekEvent`.
 */
sealed interface HistoryEvent {
    data object LoadMore : HistoryEvent
    data class OpenDay(val date: LocalDate) : HistoryEvent
    data object Retry : HistoryEvent
}
