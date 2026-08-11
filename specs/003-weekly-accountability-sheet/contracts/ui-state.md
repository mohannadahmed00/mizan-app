# Contract: UI State

**Feature**: 003-weekly-accountability-sheet | **Date**: 2026-08-11

Two new screens. Each has one immutable state exposed as `StateFlow`, following `002`'s
`TodayUiState` shape: derived values are computed properties, never stored fields, so nothing can
drift out of step.

---

## WeekUiState

```kotlin
data class WeekUiState(
    val status: Status = Status.Loading,
    val weekKey: WeekKey? = null,
    val startDate: LocalDate? = null,
    val days: List<DayCellUi> = emptyList(),      // exactly 7 when Ready
    val earnedPoints: Int = 0,
    val elapsedAvailablePoints: Int = 0,
    val weekTargetPoints: Int = 0,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
) {
    val progressFraction: Float
        get() = if (elapsedAvailablePoints == 0) 0f
                else earnedPoints.toFloat() / elapsedAvailablePoints

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data class CouldNotLoad(val detail: String) : Status
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

data class DayCellUi(
    val date: LocalDate,
    val dayLabel: String,               // "Sat", "Sun", … — shell language, English
    val hijriLabel: String?,            // null only when there is no stored plan
    val earnedPoints: Int,
    val availablePoints: Int,
    val state: DayCellState,
) {
    val isOpenable: Boolean get() = state != DayCellState.OUTSIDE_RECORD &&
                                    state != DayCellState.NOT_YET_ELAPSED
}
```

**Rules**

1. `progressFraction` divides by `elapsedAvailablePoints`, **never** by `weekTargetPoints`
   (FR-009a). The target is a separate field precisely so it cannot be reached by the divide.
2. `weekTargetPoints` is rendered as context and never as the headline denominator (FR-009b), and is
   never framed as a shortfall — no "X to go", no "behind by Y".
3. `days` holds exactly seven entries in Saturday-to-Friday order whenever `status` is `Ready`,
   including entries for `OUTSIDE_RECORD` and `NOT_YET_ELAPSED` dates. The week is always seven
   cells; the cells differ in state, never in presence (FR-003, FR-014).
4. `CouldNotLoad` is a distinct status from `CatalogueUnavailable` and from an empty week. No status
   renders as a week with fewer than seven cells or with a total over an incomplete set (FR-014b).
5. `canGoPrevious` / `canGoNext` are computed by the ViewModel from the record start and the current
   date. At a bound the affordance is simply unavailable — never an error, never a message
   (FR-018).
6. Nothing here persists. The viewed week is not saved; the screen opens on the current week every
   time (FR-019).

```kotlin
sealed interface WeekEvent {
    data object PreviousWeek : WeekEvent
    data object NextWeek : WeekEvent
    data class OpenDay(val date: LocalDate) : WeekEvent
    data object Retry : WeekEvent
}
```

**There is deliberately no event for completing, undoing, adding, removing, reordering or repricing
anything** — Principle VI and FR-024 made structural rather than merely unimplemented, exactly as
`002` did for `TodayEvent`. `OpenDay` navigates; it does not write.

---

## DaySummaryUiState

```kotlin
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
        data object NoRecord : Status
    }
}

data class SummarySectionUi(
    val id: String,
    val label: String,
    val tasks: List<SummaryTaskUi>,
)

data class SummaryTaskUi(
    val slug: String,
    val label: String,                  // Arabic content, from the plan's snapshot
    val points: Int,
    val recordedCount: Int,
    val maxOccurrences: Int,
) {
    val isComplete: Boolean get() = recordedCount >= maxOccurrences
    val isMultiOccurrence: Boolean get() = maxOccurrences > 1
}
```

**Rules**

1. Every field originates in the **stored plan** and its completions. Nothing is read from the live
   catalogue, so a catalogue change cannot alter a rendered past day (FR-023).
2. `SummaryTaskUi` deliberately has **no `canUndo`**. `002`'s `TaskRowUi` has one; its absence here
   is the type-level statement that this screen cannot write (FR-024).
3. `NoRecord` covers a date with no plan — before the record start, or in the future. It is a plain
   statement that there is nothing recorded, never a failure state (FR-016, Principle IX).
4. There is **no event type at all** for this screen. Back is handled by the host. A screen with no
   events cannot record, and cannot be extended into one by accident.

---

## Navigation state (`:app`)

No navigation library (research.md R3). `MainActivity` holds:

```kotlin
sealed interface Destination {
    data object Today : Destination
    data object Week : Destination
    data class DaySummary(val date: LocalDate) : Destination
}
```

Held in `rememberSaveable` so it survives configuration change and process death. Back from
`DaySummary` returns to `Week`; back from `Week` returns to `Today`; back from `Today` exits.

`002`'s `LaunchedEffect` calling `viewModel.refreshForCurrentDate()` on `RESUMED` stays on the Today
route unchanged. The week route does the same for its own rollover check (FR-020) — crossing local
midnight into a new week moves the sheet to the new week.

---

## Cross-cutting

**One immutable state per screen, exposed as `StateFlow`.** No mutable state leaves either
ViewModel (constitution, Technology Constraints).

**Arabic task content** appears in `SummaryTaskUi.label` only. It renders in the Arabic face with its
own direction and must not reflow the surrounding layout (FR-021). The week sheet itself carries no
Arabic — day names and figures are shell content.

**Principle IX audit surface.** Every state either screen can show is enumerated above:
five `DayCellState` values, four `WeekUiState.Status` values, three `DaySummaryUiState.Status`
values. SC-011 requires reviewing every one for a negative quantity, a penalty, or an implication of
fault — a finite, checkable list rather than a promise.
