# Contract: UI State and Navigation

One new screen state, one widened existing state, and the navigation change FR-015/FR-015a require.

---

## `HistoryUiState` — `app/history/HistoryUiState.kt`

```
data class HistoryUiState(
    val status: Status = Status.Loading,
    val weeks: List<WeekRowUi> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
) {
    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data object RecordNotStarted : Status          // FR-007
        data class CouldNotLoad(val detail: String) : Status
        data class CatalogueUnavailable(val detail: String) : Status
    }
}

data class WeekRowUi(
    val weekKey: WeekKey,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val earnedPoints: Int,
    val availablePoints: Int,
    val days: List<DayCellUi>,        // exactly 7, reusing 003's DayCellUi
)
```

**Rules**

- `days` reuses `003`'s `DayCellUi` and its `DayCellState` unchanged — all four states FR-020a
  requires already exist, and reusing them is what stops history and the weekly sheet from developing
  different opinions about the same date.
- `availablePoints` on a past week is the week's whole target. On the **current** week it is the
  elapsed availability, matching `003`'s `WeekUiState.progressFraction` rule — a Sunday must not read
  as 14% of a week on either surface.
- `isLoadingMore` is distinct from `Status.Loading`: appending a page must not blank the list or move
  the user's position (FR-005).
- `RecordNotStarted` is a first-class status, not an empty `Ready`. FR-007 requires a statement and a
  way to start, not a blank list.

```
sealed interface HistoryEvent {
    data object LoadMore : HistoryEvent
    data class OpenDay(val date: LocalDate) : HistoryEvent
    data object Retry : HistoryEvent
}
```

There is deliberately **no event for completing, undoing, adding, removing, reordering or repricing
anything** — Principle VI made structural, exactly as `002` did for `TodayEvent` and `003` for
`WeekEvent`. `OpenDay` is handled by the host, as `WeekEvent.OpenDay` already is.

---

## `DaySummaryUiState` — changed

`003`'s state, with two additions and one deliberate non-addition.

```
data class DaySummaryUiState(
    val status: Status = Status.Loading,
    val civilDate: LocalDate? = null,
    val hijriLabel: String? = null,
    val sections: List<SummarySectionUi> = emptyList(),
    val earnedPoints: Int = 0,
    val availablePoints: Int = 0,
) {
    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data object NoRecord : Status
        data class CatalogueUnavailable(val detail: String) : Status   // NEW — FR-032, R4
    }
}
```

**Still no event type at all.** `003` made its absence the type-level statement that this screen
cannot write, and FR-024 keeps that true. The locked-day copy FR-024 requires is static text on the
screen, not state — it is true of every date this screen can ever show, so branching on it would be a
condition that is always the same.

**Still no `isDerived` flag.** FR-014 forbids surfacing a plan's origin, and a field nothing may
branch on is a field that will eventually be branched on.

---

## Navigation — `MainActivity`

### Before

```
var destination by rememberSaveable(...) { mutableStateOf<Destination>(Destination.Today) }
// DaySummary's BackHandler always returns to Week
```

### After (R5)

```
sealed interface Destination {
    data object Today : Destination
    data object Week : Destination
    data object History : Destination                       // NEW
    data class DaySummary(val date: LocalDate) : Destination
}

var stack by rememberSaveable(stateSaver = StackSaver) {
    mutableStateOf(listOf<Destination>(Destination.Today))
}
```

This is the **end state**. `tasks.md` reaches it in two steps on purpose: User Story 1 makes history
reachable using the existing single-destination field (T033a), so it ships as a complete increment,
and User Story 4 replaces that with the stack (T063–T066) once FR-015 requires returning to the
originating list. `Destination.History`, its `"HISTORY"` encoding and `HistoryRoute` are written once,
in the first step.

- One `BackHandler` at the host pops the stack; per-screen hard-coded back targets are removed.
- `DestinationSaver`'s existing string encoding is reused, joined with a separator for the list.
  `Destination.History` encodes as `"HISTORY"`.
- Back from a `DaySummary` returns to whichever of `Week` or `History` pushed it (FR-015).

### Routing today (FR-015a, R6)

Both `WeekEvent.OpenDay` and `HistoryEvent.OpenDay` route through one place:

| Date | Destination |
|---|---|
| `date == time.today()` | Push `Destination.Today` — the recording surface |
| Otherwise | Push `Destination.DaySummary(date)` |

This makes FR-023's single write surface true at the navigation layer: there is exactly one
destination in the app on which a completion can be recorded, and every route to a writable date
arrives at it (SC-013a).

Returning from `Today` pushed this way pops back to the originating list. `Today` at the root of the
stack keeps its existing behaviour — back exits the app.

### Entry point into history (FR-001)

`WeekScreen` gains one affordance leading to `Destination.History`. History is reached from the
weekly sheet rather than from a tab, because the three-tab shell in the design belongs to a later
phase and adding a navigation architecture here would be the speculative abstraction Principle VIII
forbids.

---

## Encouragement audit surface (FR-029, FR-030, SC-014)

Every state introduced above that a user can see, and what forbids a failure reading in it:

| State | Requirement |
|---|---|
| A week row totalling 0 | FR-029 — a fact, no red, no cross, no "missed" |
| Twelve consecutive zero weeks after a lapse | FR-030 — a record with gaps, never a sequence of failures; nothing summarises or characterises it |
| `NOTHING_RECORDED` day cells | Already neutral in `003`'s `DayCellState`; unchanged |
| A task row with count 0 on the day detail | FR-012 — its value and a zero, no fault language |
| `RecordNotStarted` | FR-007 — a beginning with a way to start |
| The locked-day copy | FR-024 — plain, no reprimand |
| `CouldNotLoad` / `CatalogueUnavailable` | FR-031, FR-032 — the app's failure, with a retry, never an empty record presented as an empty history |
