# Implementation Plan: Streaks & Consistency

**Branch**: `spec/004-streaks-consistency` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-streaks-consistency/spec.md`

## Summary

The last MVP increment, and the smallest. A streak is a fold over records that already exist, so
this plan adds **no table, no column, and no migration** — the schema stays at version 2. What it
adds is one indexed read, one pure domain fold, and one element on a screen that already exists.

Four technical decisions carry it:

1. **Consistency Days come from a `DISTINCT creditedDate` query, not from the completion log.** The
   fold needs to know *which dates* have a live record, not what was recorded on them. Over three
   years that is ~1,095 dates instead of ~65,000 completions, and `completions.creditedDate` is
   already indexed from `002`. Exposed as a `Flow`, so Room's own invalidation makes FR-023 —
   figures updating on record and undo — fall out rather than be wired.
2. **The at-risk and midnight transitions are scheduled, not polled.** FR-026 and FR-017 require the
   state to change while the app sits open. The use case computes the next boundary instant (20:00,
   then local midnight) and re-emits when it arrives. One `delay`, no ticker, and testable on a
   virtual clock. See research R2.
3. **The streak becomes a nested panel inside `TodayUiState` with its own status.** FR-018b requires
   it to survive the catalogue being unavailable, and `TodayViewModel` currently *replaces* its
   whole state on that path. That is the one piece of existing behaviour this increment restructures.
   See research R3.
4. **Nothing is stored, including whether the break notice has been shown.** Its seven-day window is
   read out of the last active date (FR-021a), so the feature keeps its central property: delete the
   derived code and the record is untouched.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged from `003`)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2. **No new
dependency.** One build-file line adds the already-catalogued `kotlinx-coroutines-test` to
`:domain`'s test configuration — see research R2.

**Storage**: Room, offline-only. **No schema change.** Database stays at version 2, no migration, no
new schema export. One new read-only DAO query.

**Testing**: JUnit 4. `:domain` and `:app` on the JVM, `:data` instrumented on device/emulator.
Virtual-time tests for the boundary scheduling (`runTest`). Compose UI tests for the streak element.

**Target Platform**: Android, `minSdk 24`, `compileSdk 37`, `targetSdk 36`

**Project Type**: Android application, multi-module by layer — `:app` → `:data` → `:domain`

**Performance Goals**: SC-014 — figures produced within 100 ms on a mid-range device against three
years of daily completions (~1,095 consistency dates, ~65,000 completion rows). One indexed
`DISTINCT` query plus one linear pass. SC-017 — the day's tasks appear no later than they do today,
which holds because the streak subscribes on its own and never gates the task collector.

**Constraints**: `:domain` keeps zero Android on its classpath. No writes on any path in this
feature. No network. No stored streak value of any kind, including a "notice shown" flag.

**Scale/Scope**: 0 new screens, 0 migrations, 1 new domain package (`streak`), 1 new DAO query, 1
restructured ViewModel, 28 functional requirements. No new module.

## What `002` and `003` already provide

Verified against the merged code on `develop-v1`, not against their documents.

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Live completions filtered of tombstones | ✅ every read in `CompletionDao` filters `reversedAt IS NULL AND deletedAt IS NULL` |
| An index supporting a date-keyed scan of completions | ✅ `Index(value = ["creditedDate"])` on `completions` |
| Record start | ✅ `DayPlanRepository.earliestPlanDate()` — added by `003` |
| Whether a plan means the user was present | ✅ `PlanOrigin.OPENED / BACKFILLED` on `DayPlan` — added by `003` for exactly this phase |
| Injected clock, including instant and zone | ✅ `TimeProvider.now()`, `today()`, `zone()`; `DayBoundary.dateAt` |
| A screen to hang the element on | ✅ `TodayScreen` + `TodayViewModel` + `TodayUiState` |
| Undo reflected immediately in a screen | ✅ `observeCompletions(date)` — but scoped to one date, so not reusable here |
| The set of dates carrying a live completion | ❌ every completion read is single-date or single-week — new query here |
| A UI state that survives `CatalogueUnavailable` | ❌ `TodayViewModel` replaces the whole state on that path — R3 |
| Re-evaluation while the app sits open | ❌ `refreshForCurrentDate()` runs on resume only — R2 |
| Glossary entries for Longest Streak / Streak Break | ❌ `docs/PLAN.md` names both; `docs/GLOSSARY.md` defines neither — R6 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer: `:domain` fold, boundary and window tests → domain code → `:data` query test → the DAO query → ViewModel tests → ViewModel restructure → **Compose UI test → the streak element**. Only Koin wiring and `@Preview` claim the exemption. The `TodayViewModel` restructure (R3) is behaviour change on existing code, so its new cases are written first and the existing `TodayViewModelTest` must stay green throughout. |
| **II — Domain purity** | Yes | `ConsistencyDay`, `StreakSummary`, `buildStreakSummary`, `RecentActivity` and the boundary calculation are pure Kotlin in `:domain`, a `kotlin("jvm")` module where an Android import cannot compile. `GetStreakSummary` depends only on the two repository interfaces and `TimeProvider`. The one non-pure element — waiting for the next boundary — is `kotlinx.coroutines.delay`, which the constitution names as permitted. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | This feature writes nothing: no DAO write method is added, no repository gains a write, and the use case has no creation path. Persistence and the catalogue are untouched, so no migration exists to be destructive. The principle's *test* obligation is still honoured through SC-009a — a `:data` test bumps the catalogue's points and schedules and asserts every streak figure is unchanged, which is the only way the streak could be reading the catalogue. |
| **IV — Offline-first** | Yes | No network. Every figure comes from Room. FR-018c and FR-021b keep the streak strictly off the critical path — the day's tasks paint first, and a streak that cannot be read costs the user their figures, never their ability to record. |
| **V — Backend independence** | Yes | No new row, no new column, no identifier. The new read is a projection over `completions`, which already carries UUID id, `updatedAt`, `deletedAt` and `userId`. A backend arriving later changes an implementation, not the `CompletionRepository` shape. |
| **VI — Fixed content** | Yes | The whole feature is a read. No event added to `TodayEvent` can author anything — the only new event is `RetryStreak`, which re-subscribes to a read. |
| **VII — Deterministic time** | Yes | `TimeProvider` stays the only clock. `DayBoundary` stays the only date rule. The 20:00 threshold and the next-boundary calculation live in one file in `:domain/streak/` and nowhere else. Every transition in FR-025, FR-026 and FR-017 is exercised by advancing a fake clock and virtual time. |
| **VIII — Vertical slices** | Yes | Four stories shipping as one usable capability. No cached streak row (`docs/PLAN.md`, spec Assumptions). No streak detail sheet. No notification. No new module and no new screen. |
| **IX — Encouragement** | Yes | The principle at greatest risk here, load-bearing in four places: the ended run (FR-021, FR-021a), the zero streak (FR-022), the at-risk nudge (FR-027), and a figure that fails to load (FR-021b, which forbids both a silent disappearance and a 0). Four neutral indicator states, no red, no cross, no broken-chain imagery. Audited against the `CLAUDE.md` design list. |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`,
nothing mutable exposed ✓. Module direction unchanged ✓. Koin sole DI, no Hilt/KSP added ✓. Room
untouched — no migration to be destructive ✓. No new network surface ✓. Arabic content beside the
streak element keeps `002`'s bidirectional handling ✓.

**Gate result: PASS.** No violations. Complexity Tracking is empty.

## Spec corrections applied

None. Every claim in `spec.md` was checked against the merged code and held:

| Claim checked | Reality |
|---|---|
| "`002`'s write rule permits a completion only on the current date." | `DayWritePolicy.isWritable(date) = date == time.today()`, consulted by `CompletionRepository.record` and `undoLast` before touching storage. The spec's premise for FR-001 is sound. |
| "`003` records how a plan came into being." | `PlanOrigin` on `DayPlan`, persisted as `day_plans.origin`. Available if wanted; FR-004 means it is not consulted. |
| "This increment adds no persistence." | Confirmed — nothing in FR-001 … FR-028 requires a write. Schema stays at version 2. |
| "The record start exists." | `DayPlanRepository.earliestPlanDate(): LocalDate?`. |

## Project Structure

### Documentation (this feature)

```text
specs/004-streaks-consistency/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── repositories.md  # the one added read
│   └── ui-state.md      # TodayUiState changes, StreakPanelUi
├── checklists/requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

Only additions and the marked changes. Everything else is `002`'s and `003`'s, untouched.

```text
domain/
├── build.gradle.kts                          # CHANGED — testImplementation(coroutines-test), R2
└── src/
    ├── main/kotlin/com/giraffe/mizanapp/domain/
    │   ├── streak/
    │   │   ├── ConsistencyDay.kt             # NEW — the rule, over dates (FR-001..FR-005)
    │   │   ├── StreakSummary.kt              # NEW — current, longest, lastActive, todayCounted…
    │   │   ├── BuildStreakSummary.kt         # NEW — the pure fold (FR-006..FR-011)
    │   │   ├── RecentActivity.kt             # NEW — seven-day window, four states (FR-020a)
    │   │   └── StreakClock.kt                # NEW — 20:00 threshold + next boundary (FR-025, R2)
    │   ├── repository/
    │   │   └── CompletionRepository.kt       # CHANGED — + observeConsistencyDates()
    │   └── usecase/
    │       └── GetStreakSummary.kt           # NEW — flow: dates × record start × clock boundaries
    └── test/kotlin/com/giraffe/mizanapp/domain/
        ├── streak/                           # NEW — fold, window, break window, at-risk tests
        └── usecase/GetStreakSummaryTest.kt   # NEW — virtual-time boundary re-emission

data/src/
├── main/kotlin/com/giraffe/mizanapp/data/
│   ├── db/daos/CompletionDao.kt              # CHANGED — + observeLiveDates() (DISTINCT, indexed)
│   └── repository/RoomCompletionRepository.kt # CHANGED — + observeConsistencyDates()
└── androidTest/kotlin/com/giraffe/mizanapp/data/
    ├── ConsistencyDatesQueryTest.kt          # NEW — distinct, tombstones excluded, ordering
    ├── StreakBackfillTest.kt                 # NEW — a backfilled plan contributes nothing (SC-005)
    └── StreakImmutabilityTest.kt             # NEW — SC-009a, catalogue bump moves no figure

app/src/
├── main/java/com/giraffe/mizanapp/
│   ├── di/Modules.kt                         # CHANGED — GetStreakSummary + TodayViewModel arity
│   └── today/
│       ├── TodayUiState.kt                   # CHANGED — + streak panel, + RetryStreak event
│       ├── TodayViewModel.kt                 # CHANGED — streak collector; state no longer replaced
│       └── StreakElement.kt                  # NEW — the composable (FR-018a..FR-022, FR-027)
├── test/java/com/giraffe/mizanapp/today/
│   ├── FakeRepositories.kt                   # CHANGED — + consistency dates
│   └── TodayStreakTest.kt                    # NEW — pending, failure, retry, break window
└── androidTest/java/com/giraffe/mizanapp/today/
    ├── StreakElementTest.kt                  # NEW — the panel's states in isolation, no red
    └── TodayScreenStreakTest.kt              # NEW — element unchanged across blocks (SC-016)

docs/GLOSSARY.md                              # CHANGED — + Longest Streak, + Streak Break (R6)
```

**On Compose UI tests**: Principle I exempts only DI wiring, `@Preview` composables, and generated
code. The streak element is none of those, so `StreakElementTest` precedes `StreakElement.kt` in
tasks.md. `003` established this; `002`'s missing `TodayScreen` test is a gap in `002`, not a
precedent.

**Structure Decision**: no new Gradle module and no new screen. A `streak` package in `:domain`,
one query in `:data`, one composable in `:app/today/`. The streak belongs on Today rather than in a
surface of its own — spec Assumptions rule out the detail sheet, and Principle VIII forbids a screen
whose content is four numbers already on display.

The one structural judgement is putting the **boundary schedule** in `GetStreakSummary` rather than
in `TodayViewModel`. "Re-evaluate when the clock crosses 20:00 or midnight" is a time rule, and
Principle VII requires time rules to have exactly one home; putting it in `:app` would place it
where no domain test-first discipline applies and where a second consumer could later disagree with
it. The cost is one test-only dependency line in `:domain`. See research R2.

## Constitution Re-Check (post-Phase 1 design)

Design introduced four things absent at the first gate. Each re-checked:

| Introduced | Principle at risk | Verdict |
|---|---|---|
| `CompletionDao.observeLiveDates()` — a `DISTINCT` scan over the whole table | IV, VIII | **Pass.** Read-only, covered by the existing `creditedDate` index, and it replaces reading ~65,000 rows to learn ~1,095 facts. It is on its own subscription, so FR-018c holds: the task collector never waits for it. |
| `GetStreakSummary` suspends on a scheduled boundary | II, VII | **Pass.** `delay` is coroutines, which Principle II permits by name. The boundary instants come from `TimeProvider` through `StreakClock`, so no new code reads a clock and the rule has one home. Virtual time makes every transition testable. |
| `TodayUiState` gains a nested panel; `TodayViewModel` stops replacing its state | I | **Pass, and the riskiest change here.** It alters shipped behaviour, so it is driven by tests written first, and the existing `TodayViewModelTest` cases are the regression net. Nothing about the day's tasks, scoring, or rollover changes — only that a status transition now preserves a field beside it. |
| A `RetryStreak` event on a read-only screen | VI | **Pass.** It re-subscribes to a read. `TodayEvent` still cannot express creating, editing, deleting, reordering or repricing anything. |

**Gate result: PASS.** No new violations. Complexity Tracking remains empty.

## Complexity Tracking

> No constitution violations and no deviation from `docs/PLAN.md`. This section is intentionally
> empty.

`docs/PLAN.md` Phase 4 says "Derive from completions first — no new source of truth", and permits a
cached streak row "only if the derived query becomes visibly slow". No measurement exists, so no
cache is built. It also specifies `GetStreakUseCase` as "a pure fold over consistency days" with an
injected clock and no Android dependencies — which is what `BuildStreakSummary` and
`GetStreakSummary` are, split so the fold stays synchronously testable and the flow plumbing stays
out of it.
