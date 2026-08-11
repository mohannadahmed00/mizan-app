package com.giraffe.mizanapp.daysummary

import java.time.LocalDate

/**
 * One immutable state for the Day Summary screen, exposed as
 * [kotlinx.coroutines.flow.StateFlow].
 *
 * There is deliberately **no event type for this screen at all** — the
 * type-level statement that it cannot record, undo, add, remove, or reorder
 * anything (FR-024, Principle VI). Back is handled by the host.
 */
data class DaySummaryUiState(
    val status: Status = Status.Loading,
    val civilDate: LocalDate? = null,
    val hijriLabel: String? = null,
    val sections: List<SummarySectionUi> = emptyList(),
    val earnedPoints: Int = 0,
    val availablePoints: Int = 0,
) {
    val progressFraction: Float
        get() = if (availablePoints == 0) 0f else earnedPoints.toFloat() / availablePoints

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status

        /** A date with no stored plan — before the record start, or in the future. Not a failure. */
        data object NoRecord : Status
    }
}

data class SummarySectionUi(
    val id: String,
    val label: String,
    val tasks: List<SummaryTaskUi>,
)

/**
 * Deliberately carries **no `canUndo`**, unlike `002`'s `TaskRowUi` — its
 * absence is the type-level statement that this screen cannot write.
 */
data class SummaryTaskUi(
    val slug: String,
    val label: String,
    val points: Int,
    val recordedCount: Int,
    val maxOccurrences: Int,
) {
    val isComplete: Boolean get() = recordedCount >= maxOccurrences
    val isMultiOccurrence: Boolean get() = maxOccurrences > 1
}
