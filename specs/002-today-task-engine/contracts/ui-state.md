# Contract: Today Screen UI State

**Feature**: 002-today-task-engine | **Date**: 2026-08-09

One immutable state per screen, exposed as `StateFlow`. No mutable state leaves the ViewModel
(constitution, Technology Constraints).

---

## The state

```kotlin
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
```

Derived values are computed properties, not stored fields — one source of truth per fact, so nothing
can drift out of step.

---

## Events

```kotlin
sealed interface TodayEvent {
    data class CompleteTask(val slug: String) : TodayEvent
    data class UndoTask(val slug: String) : TodayEvent
    data object NextSection : TodayEvent
    data object PreviousSection : TodayEvent
}
```

Four events. There is deliberately no event for creating, editing, deleting, reordering or repricing
a task — Principle VI made structural rather than merely unimplemented.

---

## Behavioural guarantees

1. **`Loading` is transient and brief.** It covers reading the plan from storage on open, never a
   network call — there is none.
2. **`CatalogueUnavailable` is distinct from an empty day** (FR-003). A day with zero tasks and a
   day the app could not load must never look alike.
3. **Completion updates the state without a reload.** The screen observes storage, so recording
   flows back through the same path that populated it (FR-022).
4. **`currentSectionIndex` is derived on load**, never persisted: the earliest section with an
   incomplete task, or 0 when everything is complete (FR-020b). Recomputed on every open, not
   restored from saved state.
5. **Navigation clamps.** `NextSection` at the last section and `PreviousSection` at the first do
   nothing and surface no error (US3 scenario 3).
6. **Rollover replaces the state.** When the date changes while the app runs, the ViewModel emits a
   state for the new date with a freshly derived section index (FR-023).
7. **`earnedPoints` never exceeds `availablePoints`, and neither is ever negative** (FR-018). Both
   come from the domain, which enforces it; the UI does no arithmetic of its own beyond the fraction.
8. **`recordedCount` counts live completions only.** Tombstones are invisible here, as everywhere
   (research.md R5).

---

## What the UI must not do

- **No negative or penalising presentation** (FR-024, Principle IX). No red incomplete states, no
  ✗ marks, no empty-ring-as-failure, no count of what was missed. An untouched day shows 0 of *n*
  neutrally. `isAtLimit` means done, never "full" or "closed".
- **No arithmetic.** The screen displays `earnedPoints` and `availablePoints`; it sums nothing
  itself. `progressFraction` is the sole exception and is pure division.
- **No clock reading.** The ViewModel receives dates from the domain's `TimeProvider`
  (Principle VII).
- **No task authoring affordance.** No add button, no swipe-to-delete, no drag handle, no long-press
  edit — anywhere on the screen (Principle VI).
- **No blocking.** Every event returns immediately; storage writes are dispatched (SC-008).

---

## Rendering the content

Task and section labels are **Arabic content**, not interface strings. Each is rendered in an
Arabic-appropriate typeface with its own right-to-left direction, inside the English left-to-right
shell, so a mixed row does not reflow the layout around it (constitution v1.1.1; tokens and the
audit list in `CLAUDE.md`).

Interface chrome — buttons, headings, the points header — is English and left-to-right.
