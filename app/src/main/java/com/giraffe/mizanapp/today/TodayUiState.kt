package com.giraffe.mizanapp.today

import java.time.LocalDate

/**
 * One immutable state for the Today screen.
 *
 * Derived values are computed properties rather than stored fields — one source
 * of truth per fact, so nothing can drift out of step.
 */
data class TodayUiState(
    val status: Status = Status.Loading,
    val civilDate: LocalDate? = null,
    val hijriLabel: String? = null,
    val sections: List<SectionUi> = emptyList(),
    val currentSectionIndex: Int = 0,
    val earnedPoints: Int = 0,
    val availablePoints: Int = 0,
) {
    val progressFraction: Float
        get() = if (availablePoints == 0) 0f else earnedPoints.toFloat() / availablePoints

    val currentSection: SectionUi? get() = sections.getOrNull(currentSectionIndex)
    val hasPreviousSection: Boolean get() = currentSectionIndex > 0
    val hasNextSection: Boolean get() = currentSectionIndex < sections.lastIndex

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status

        /** Distinct from an empty day: the two must never look alike (FR-003). */
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

data class SectionUi(
    val id: String,
    val label: String,
    val tasks: List<TaskRowUi>,
) {
    val isComplete: Boolean get() = tasks.all { it.isAtLimit }
}

data class TaskRowUi(
    val slug: String,
    val label: String,
    val points: Int,
    val recordedCount: Int,
    val maxOccurrences: Int,
) {
    val isAtLimit: Boolean get() = recordedCount >= maxOccurrences
    val isMultiOccurrence: Boolean get() = maxOccurrences > 1
    val canUndo: Boolean get() = recordedCount > 0
}

/**
 * Everything the user can do on this screen.
 *
 * There is deliberately no event for creating, editing, deleting, reordering or
 * repricing a task — Principle VI made structural rather than merely
 * unimplemented.
 */
sealed interface TodayEvent {
    data class CompleteTask(val slug: String) : TodayEvent
    data class UndoTask(val slug: String) : TodayEvent
    data object NextSection : TodayEvent
    data object PreviousSection : TodayEvent
}
