# Phase 0 Research: Streaks & Consistency

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-15

Seven questions the plan had to answer before design. Each was resolved against the merged code on
`develop-v1`, not against `002`'s or `003`'s documents.

---

## R1 — Where do Consistency Days come from?

**Decision**: a new read on `CompletionRepository`:

```
fun observeConsistencyDates(): Flow<List<LocalDate>>
```

backed by `SELECT DISTINCT creditedDate FROM completions WHERE reversedAt IS NULL AND deletedAt IS
NULL ORDER BY creditedDate`. The fold consumes dates; it never sees a completion.

**Rationale**:

- FR-001 makes consistency a fact about *dates*, not about what was recorded on them. Reading
  completions to learn which dates have one is reading ~65,000 rows to produce ~1,095 facts over
  three years — the difference between comfortably inside SC-014's 100 ms and not.
- `completions` already carries `Index(value = ["creditedDate"])` from `002`, so the `DISTINCT` scan
  is index-covered. No index is added.
- Returning a `Flow` makes FR-023 — figures updating the instant a completion is recorded or undone
  — Room's job rather than the ViewModel's. Room invalidates on any write to `completions`, and undo
  is a write (a tombstone), so the reversal case is covered by the same mechanism as the record case
  with nothing extra to wire.
- Every other read in `CompletionDao` filters `reversedAt IS NULL AND deletedAt IS NULL`; this one
  matches. FR-003 is that filter.

**Alternatives considered**:

- *Reuse `liveBetween(start, end)` over a bounded window.* Rejected: the longest streak (FR-007) is
  unbounded by definition, so any window is a wrong answer waiting for a long-running user.
- *Add a `consistency_days` table maintained on write.* Rejected outright — it is a second source of
  truth for a derived fact, `docs/PLAN.md` forbids it without a measurement, and FR-013 forbids this
  feature writing on the completion path at all.
- *Compute in the ViewModel from `observeCompletions(date)`.* Rejected: that flow is scoped to one
  date and knows nothing about history.

---

## R2 — How does the state change while the app sits open?

**Decision**: `GetStreakSummary` re-emits at the next *streak boundary* — the next of 20:00 local
and the next local midnight — by computing that instant from `TimeProvider` and suspending until it
arrives. The pure calculation lives in `StreakClock`; the use case does one `delay` and re-folds.
`:domain` gains `testImplementation(libs.kotlinx.coroutines.test)`, already in the version catalogue.

**Rationale**:

- FR-026 requires the at-risk state to clear at local midnight and FR-017 requires the figures to
  follow the new date, both with the app open. `002`'s `refreshForCurrentDate()` runs on
  `Lifecycle.State.RESUMED` only, so an app left open across 20:00 or across midnight would not
  update. That is a real gap against this spec, not a matter of taste.
- Two scheduled wake-ups a day is the whole cost. A one-second ticker would recompose a screen 86,400
  times to change it twice.
- Putting the schedule in `:domain` keeps Principle VII's "exactly one place" honest: the 20:00
  threshold and the next-boundary calculation sit in the same file, and `:app` holds no opinion about
  when a streak day turns over.
- Virtual time makes it testable. `runTest` advances past a boundary in microseconds, so SC-012's
  four transitions (19:59 → 20:00, first completion, midnight) are ordinary unit tests.

**Alternatives considered**:

- *Resume-only, as `002` does for the date.* Rejected: it leaves FR-026 and the "app open across
  midnight" edge case unimplemented, and the spec's acceptance scenarios say otherwise.
- *A periodic ticker (`flow { while(true) { emit(); delay(60_000) } }`).* Rejected: 1,440 wake-ups a
  day for two transitions, and it still needs the boundary rule to decide whether anything changed.
- *Schedule in `TodayViewModel`.* Rejected: puts a time rule in `:app`. The saving would be one
  test-only dependency line.

**Scope note**: this schedules the *streak* only. Day rollover for the task list stays exactly as
`002` built it — resume-based. Changing that is not in this increment's scope and no requirement
here asks for it.

---

## R3 — Where does the streak live in the Today state?

**Decision**: `TodayUiState` gains a nested `streak: StreakPanelUi` carrying its own status
(`Resolving` / `Ready` / `Unavailable`). `TodayViewModel` collects it on a separate job and updates
that field with `copy()`. Every path that currently assigns a fresh `TodayUiState` preserves it.

**Rationale**:

- FR-018b requires the element to be shown when the catalogue is unavailable. `TodayViewModel`
  currently does `_state.value = TodayUiState(status = CatalogueUnavailable(...))` in two places —
  a wholesale replacement that would blank any sibling field. This is the one piece of existing
  behaviour the increment has to change, and it is worth naming because it is easy to miss and
  produces a bug that only appears in a state most testing never enters.
- A separate collector is also what delivers FR-018c. The task collector and the streak collector
  subscribe independently, so the day's tasks paint the moment they are ready and the streak resolves
  when it resolves. Nothing waits for anything.
- A nested status is what lets the element be `Resolving` without the *screen* being `Loading`, which
  is exactly the distinction FR-018b draws — and it keeps the rule that one screen has one immutable
  state.

**Alternatives considered**:

- *Flat fields on `TodayUiState` (`currentStreak: Int`, …).* Rejected: an `Int` cannot express "not
  resolved yet" without borrowing 0, and FR-018c forbids showing 0 before the real figure. A nullable
  `Int` splits one fact across two fields.
- *A second `StateFlow` on the ViewModel.* Rejected: the constitution requires one immutable UI state
  per screen.
- *Its own ViewModel hosted beside `TodayScreen`.* Rejected: two ViewModels for one screen, to hold
  four numbers.

---

## R4 — What happens when the read fails?

**Decision**: the ViewModel wraps the streak flow with `catch`, emitting `StreakPanelUi.Unavailable`.
A `TodayEvent.RetryStreak` re-subscribes. No error type is added to `:domain`, and no other part of
the screen is affected.

**Rationale**:

- FR-021b requires a visible notice with a retry, and forbids both a silent disappearance and a 0.
  A silently vanished 38 reads as a lost 38, which is a Principle IX failure produced by an
  implementation shortcut rather than by a design choice.
- Re-subscription is the whole retry: the flow is a Room query, so a new collection re-runs it. There
  is nothing to reset.
- This is deliberately a *different* posture from `003`, where an unloadable week takes the whole
  screen. There, the figures were the screen; here they sit beside the core loop, and Principle IV
  forbids an ornament taking down the ability to record. Planning should not harmonise the two.

**Alternatives considered**:

- *A `Result` type through `:domain`.* Rejected: the domain fold cannot fail — it is a pure function
  over a list. Only the read fails, and that is an infrastructure fact best caught where the
  subscription lives.
- *Let it crash.* Not a serious option, but worth recording that the current `TodayViewModel` has no
  `catch` anywhere; the streak collector is the first place one is needed, because it is the first
  read that is optional to the screen's usefulness.

---

## R5 — Is one pass over the dates fast enough, and does anything need caching?

**Decision**: no cache. `buildStreakSummary` is a single linear pass over the sorted distinct dates,
computing the current run, the longest run, and the last active date together.

**Rationale**:

- Three years of unbroken daily use is 1,095 `LocalDate` values. Sorting is done by SQLite via
  `ORDER BY`; the fold is one traversal comparing each date to its predecessor. SC-014's 100 ms
  budget is not close.
- `docs/PLAN.md` permits a cached streak row "only if the derived query becomes visibly slow", and
  Principle VIII forbids it until a measurement says so. No measurement exists.
- If one ever does, the cache must be reconstructible from the log and never authoritative — the
  spec's Assumptions already fix that, and nothing in this design makes it harder to add later.

**Alternatives considered**:

- *Compute the longest streak incrementally on write.* Rejected: a write on the completion path is
  precisely what `docs/PLAN.md`'s definition of done for this phase forbids.
- *Bound the fold to the last N days.* Rejected: FR-007 is unbounded.

---

## R6 — "Best streak" or "longest streak", and what does the glossary owe?

**Decision**: **Longest Streak** is canonical in the spec, in test names and in type names, matching
`docs/PLAN.md`. "Best" survives only as on-screen copy. `docs/GLOSSARY.md` gains two entries —
Longest Streak and Streak Break.

**Rationale**:

- `docs/GLOSSARY.md` opens by stating that each term means exactly one thing "in a specification, a
  test name, and a type name". Two names for one figure is the failure that rule exists to prevent.
- `docs/PLAN.md` lists Consistency Day, Streak, **Longest Streak** and **Streak Break** as the
  concepts Phase 4 introduces. The glossary defines the first two and neither of the last two, so it
  is incomplete until this increment lands. That makes the glossary edit a deliverable, not tidying.
- Keeping "best" available as copy costs nothing and is the friendlier word in the one place the user
  reads it.

**Alternatives considered**:

- *Rename to Best Streak everywhere and amend `docs/PLAN.md`.* Rejected by the author during
  clarification; it also amends the roadmap to match a draft rather than the reverse.
- *Use "longest streak" on screen too.* Rejected: stiffer than it needs to be in the only place the
  wording is a product decision rather than a naming one.

---

## R7 — How is Principle III satisfied by an increment that changes no schema?

**Decision**: the mandatory historical-immutability test is discharged by `StreakImmutabilityTest`
in `:data` — seed history, bump the catalogue's point values and schedule rules, and assert every
streak figure is byte-identical before and after (SC-009a).

**Rationale**:

- The constitution requires the test of "any change touching persistence or the task catalogue". This
  increment touches neither: no table, no column, no migration, and no catalogue read. The letter of
  the obligation is not triggered.
- Its *substance* still is. The only mechanism by which a streak figure could move under a catalogue
  change is the streak reading the catalogue — which FR-005 forbids and which nothing else would
  catch, because the figures would still look plausible. A test that would fail if FR-005 were
  violated is worth more here than one that proves an absent migration is non-destructive.
- It also protects the next increment: Phase 5's retro-completion changes what a Consistency Day
  means, and this test is what will show whether it changed anything it should not have.

**Alternatives considered**:

- *Skip it, since no schema changed.* Rejected: technically permissible and substantively wrong, and
  the merge gate in `CLAUDE.md` asks whether the increment "touched persistence or the task
  catalogue" — a reviewer reading a streak feature would reasonably expect an answer.
- *Put it in `:domain`.* Rejected: a domain test would pass a different catalogue to a pure function
  and prove only that the function ignores its arguments. The claim worth testing is about the wiring.
