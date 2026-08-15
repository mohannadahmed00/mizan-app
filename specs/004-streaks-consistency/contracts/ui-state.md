# Contract: UI State

**Feature**: 004-streaks-consistency | **Date**: 2026-08-15

One screen, and it already exists. `TodayUiState` gains one field and `TodayEvent` gains one case.
No new screen, no new ViewModel, no second `StateFlow`.

---

## TodayUiState *(changed — one field)*

```kotlin
data class TodayUiState(
    val status: Status = Status.Loading,
    val civilDate: LocalDate? = null,
    val hijriLabel: String? = null,
    val sections: List<SectionUi> = emptyList(),
    val currentSectionIndex: Int = 0,
    val earnedPoints: Int = 0,
    val availablePoints: Int = 0,
    val streak: StreakPanelUi = StreakPanelUi.Resolving,   // new
) { /* existing computed properties unchanged */ }
```

**The field is nested and carries its own status.** That is the whole design, and it exists for two
requirements:

- **FR-018b** — the element is shown when the catalogue is unavailable. `streak` sits *beside*
  `status`, not inside it, so `Status.CatalogueUnavailable` and `StreakPanelUi.Ready` coexist.
- **FR-018c** — the day's tasks paint before the figures resolve. `StreakPanelUi.Resolving` says
  "not yet" without borrowing 0 to say it, which FR-018c forbids.

**Consequence for `TodayViewModel`** — the paths that currently do `_state.value = TodayUiState(...)`
must preserve `streak`. There are three: the seed failure in `load()`, the `NoCatalogue` branch in
`openDate()`, and the whole-state construction at the end of `emit()`. This is the only existing
behaviour the increment changes; see [research R3](../research.md#r3--where-does-the-streak-live-in-the-today-state).

---

## StreakPanelUi *(new)*

```kotlin
sealed interface StreakPanelUi {

    /** Figures not yet available. Renders the element's space, never a number. */
    data object Resolving : StreakPanelUi

    data class Ready(
        val current: Int,
        val longest: Int,
        val todayCounted: Boolean,
        val isAtRisk: Boolean,
        val showBreakNotice: Boolean,
        val recentActivity: List<ActivityDayUi>,
    ) : StreakPanelUi

    /** The record could not be read (FR-021b). Never a 0, never a silent absence. */
    data class Unavailable(val detail: String) : StreakPanelUi
}

data class ActivityDayUi(
    val date: LocalDate,
    val state: ActivityState,   // COUNTED | NOT_RECORDED | TODAY_PENDING | OUTSIDE_RECORD
)
```

**Rendering rules** — each maps to a requirement, and each is what `StreakElementTest` asserts:

| State | Renders | Requirement |
|---|---|---|
| `Resolving` | the element's reserved space, no figure, no placeholder number | FR-018c |
| `Ready`, `current > 0` | the run, the longest run, seven indicator positions | FR-018, FR-020 |
| `Ready`, `todayCounted == false` | today marked pending, distinctly from the count itself | FR-019 |
| `Ready`, `current == 0`, `longest == 0` | an unstarted record — an invitation, not a sequence of failures | FR-022 |
| `Ready`, `showBreakNotice` | the longest run intact, presented as standing, with the next step named | FR-021, FR-021a |
| `Ready`, `current == 0`, notice window passed | the plain start state; nothing refers to the ended run | FR-021a |
| `Ready`, `isAtRisk` | what is still possible — no countdown, no warning colour, no language of losing | FR-027 |
| `Unavailable` | a plain notice attributing the failure to the app, plus a retry | FR-021b |

**Forbidden in every state** (FR-021, audited against the `CLAUDE.md` design list): red, a cross, a
broken or emptied chain, a negative figure, a penalty, and any language attributing fault. The four
`ActivityState` values are four neutral treatments, not two good ones and two bad ones.

**`Ready` is a flat snapshot, not a handle.** It holds no callback, no repository, and nothing lazy.
Everything on it was computed by `buildStreakSummary` before the state was assigned, which is what
lets a Compose test drive every case above without a database.

---

## TodayEvent *(changed — one case)*

```kotlin
sealed interface TodayEvent {
    data class CompleteTask(val slug: String) : TodayEvent
    data class UndoTask(val slug: String) : TodayEvent
    data object NextSection : TodayEvent
    data object PreviousSection : TodayEvent
    data object RetryStreak : TodayEvent          // new
}
```

`RetryStreak` re-subscribes to the streak flow. It is the whole recovery path for FR-021b: the source
is a Room query, so a new collection re-runs it and there is nothing to reset.

**Principle VI holds structurally.** There is still no event that can create, edit, delete, reorder
or reprice a task, and `RetryStreak` re-runs a read. `002` made this a property of the type rather
than of the screen; adding a read-retry does not weaken it.

---

## What the element does *not* get

| Absent | Why |
|---|---|
| A tap target opening a detail sheet | Spec Assumptions rule the sheet out; Principle VIII forbids a screen holding four numbers already on display |
| A dismiss control on the break notice | Dismissal is state, and FR-021a derives the window instead. It disappears on its own after seven days |
| A dismiss or snooze on the at-risk state | Same reason. It clears on the first completion or at midnight (FR-026) |
| A celebration, milestone, or badge at any figure | Phase 10. A run reaching 100 days reads as 100 days |
| A position on `WeekScreen` or `DaySummaryScreen` | Spec Assumptions — one surface, so there is nowhere for the figure to disagree with itself |
| Anything on the completion path | FR-013. Recording and undoing behave exactly as `002` built them; the streak reacts, it does not intervene |
