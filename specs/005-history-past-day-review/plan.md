# Implementation Plan: History & Past-Day Review

**Branch**: `spec/005-history-past-day-review` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-history-past-day-review/spec.md`

## Summary

The first increment past the MVP boundary, and the one that proves `002`'s versioning machinery
actually works. Like `004` it adds **no table, no column, and no migration** — the schema stays at
version 2 — but unlike `004` it is not purely derived: opening a past day may materialise that day's
plan, and that write has to be bounded, best-effort, and provably identical to the derived figures it
replaces.

Five technical decisions carry it:

1. **A derived day plan is built by the same function as a stored one, and simply not stored.**
   `buildDayPlan(catalogue, version, date, origin, newId)` already exists from `002`. History calls it
   with the version `versionEffectiveOn(date)` returns and discards the result instead of inserting
   it. FR-020b — the same date must read identically whether it carries a plan or not — becomes a
   structural property rather than a pair of code paths kept in sync by hand. See research R1.
2. **`buildWeekSummary` gains elapsed dates in its projection map.** It currently requires
   `projectedAvailable` to cover dates *after* today and silently reports `available = 0` for an
   elapsed date with no plan. That was correct for `003`, where the sheet backfilled before
   aggregating; it is wrong for history, which does not. The contract widens to "every date in the
   week with no stored plan". See research R2.
3. **A history page is a range read, not a new query.** `plansBetween` and `liveBetween` already take
   arbitrary date ranges. Eight weeks at a time is one call to each plus one catalogue load per
   distinct version in the range. No DAO method is added anywhere in this feature. See research R3.
4. **Opening a past day materialises best-effort and never gates the read.** `GetDayDetail`
   orchestrates: refuse out-of-range dates, try to store, read back, and fall through to the derived
   plan when the store failed. A failed write is invisible; only an unavailable catalogue version is
   surfaced (FR-020c, FR-032). See research R4.
5. **`MainActivity` gains a real back stack.** `002` chose a single `Destination` field and recorded
   why: a nav library was not worth it "until a third destination with its own back stack" arrived.
   It has. FR-015 and FR-015a require returning to *whichever* list a day was opened from, which a
   single field cannot express. A `List<Destination>` in `rememberSaveable` is the smallest thing
   that can. See research R5.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged from `004`)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2. **No new
dependency.** No navigation library — see research R5.

**Storage**: Room, offline-only. **No schema change.** Database stays at version 2, no migration, no
new schema export, no new DAO method. The only write is `DayPlanDao.insertPlanWithTasks`, already
reached through `DayPlanRepository.ensurePlanFor`.

**Testing**: JUnit 4. `:domain` and `:app` on the JVM, `:data` instrumented on device/emulator.
Compose UI tests for the history list and the past-day copy.

**Target Platform**: Android, `minSdk 24`, `compileSdk 37`, `targetSdk 36`

**Performance Goals**: SC-015 — first screen of history within 500 ms and any day within 300 ms on a
mid-range device against three years of data (~157 weeks, ~1,095 plans, ~44,000 planned-task rows,
~65,000 completions). A page of eight weeks is 56 dates: one `plansBetween`, one `liveBetween`, and
one catalogue load per distinct version, all over indexed date columns.

**Constraints**: `:domain` keeps zero Android on its classpath. Scrolling performs **no** write
(SC-008). No network. No new stored fact of any kind.

**Scale/Scope**: 1 new screen, 0 migrations, 0 new DAO methods, 1 new domain package (`history`), 2
new use cases, 1 changed pure function, 1 changed navigation host, 39 functional requirements.

## What `002`, `003` and `004` already provide

Verified against the merged code on `develop-v1`, not against their documents.

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Arbitrary-range reads of plans and completions | ✅ `plansBetween(start, end)`, `liveBetween(start, end)` — both indexed on date |
| Record start | ✅ `DayPlanRepository.earliestPlanDate()` |
| The version that applied on a past date | ✅ `CatalogueRepository.versionEffectiveOn(date)` — "never guesses", returns null before every version |
| Building a plan for an arbitrary date | ✅ `buildDayPlan(...)` — pure, in `:domain`, takes the version explicitly |
| Availability without persisting | ✅ `projectAvailablePoints(catalogue, version, date)` — but `003` calls it only for future dates at the current version |
| Materialising a plan for a past date | ✅ `ensurePlanFor(date)` — already uses `versionEffectiveOn`, already marks non-today plans `BACKFILLED` |
| A read-only day projection | ✅ `GetDaySummary` + `DaySummaryScreen` + `DaySummaryUiState`, which carries no event type at all |
| Neutral day states | ✅ `DayCellState` — five values, `OUTSIDE_RECORD` and `NOT_YET_ELAPSED` already distinct from `NOTHING_RECORDED` (FR-003 needs no new state) |
| One week rule | ✅ `WeekBoundary` — the only definition in the app |
| The write rule, in one place | ✅ `DayWritePolicy.isWritable(date) = date == time.today()`, consulted inside `CompletionRepository` |
| Elapsed dates with no plan reading honestly | ❌ `buildWeekSummary` reports `available = 0` for them — R2 |
| A back stack | ❌ `MainActivity` holds a single `Destination`; back is a hard-coded `BackHandler` per screen — R5 |
| Tapping today reaching the recording surface | ❌ every cell routes to `DaySummaryScreen`, today included — R6 |
| Glossary entries for Locked Day / Retro-Completion Window / Record Start | ❌ `docs/PLAN.md` names the first two; `docs/GLOSSARY.md` defines none — R7 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer: `:domain` pure tests (widened `buildWeekSummary`, page assembly, derived-equals-stored) → domain code → `:data` instrumented tests (the catalogue-change suite, best-effort store, no-writes-on-scroll) → any data change → ViewModel tests → ViewModels → **Compose UI tests → the screens**. SC-005's catalogue-change suite is the phase's defining test and is written before the code it covers. Only Koin wiring and `@Preview` claim the exemption. |
| **II — Domain purity** | Yes | `HistoryPage`, the widened `buildWeekSummary`, `GetHistoryPage` and `GetDayDetail` are Kotlin in `:domain`, a `kotlin("jvm")` module where an Android import cannot compile. Both use cases depend only on repository interfaces and `TimeProvider`. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | **This is the increment that owes this principle its proof.** No existing plan or completion is ever altered or removed: `DayPlanDao` still has no update method and `DayPlanRepository` still has no update path. The only write is inserting a plan that does not exist, through `ensurePlanFor`, which already reads `versionEffectiveOn(date)` rather than the current version (FR-018). User Story 3 and SC-005 are the mandated test: seed under v1, change points and schedules in v2, assert every recorded day, week total and streak figure is unchanged while today follows v2. |
| **IV — Offline-first** | Yes | No network anywhere. Every figure comes from Room plus the seeded catalogue. FR-031 and FR-032 keep a failure attributable and retryable rather than silent, and FR-020c keeps a failed write from costing the user the view. |
| **V — Backend independence** | Yes | No new row, no new column, no new identifier. Derived plans are never persisted and carry a non-identifying id, so nothing enters the sync surface. Repository interfaces are unchanged — this feature adds use cases, not seams. |
| **VI — Fixed content** | Yes | The whole feature is a read plus one idempotent plan insert. `DaySummaryUiState` still has no event type; `HistoryEvent` gains only navigation, paging and retry. Nothing anywhere can create, edit, delete, reorder or reprice a task. FR-022 keeps the past unwritable and FR-015a keeps exactly one surface that can write at all. |
| **VII — Deterministic time** | Yes | `TimeProvider` stays the only clock; `WeekBoundary` and `DayBoundary` stay the only calendar rules. History derives its page boundaries from `WeekBoundary` alone — FR-009 forbids a second week definition, and none is written. |
| **VIII — Vertical slices** | Yes | Four stories shipping as one usable capability. No search, no jump-to-date, no filtering, no export, no notes, no monthly rollup, no chart. One new screen, and the back stack is introduced because FR-015 needs it now — not speculatively. |
| **IX — Encouragement** | Yes | The highest-risk surface in the product for this principle: after Q1 the list is continuous, so a lapse is visible as consecutive zero weeks. FR-029 and FR-030 govern it, and `DayCellState` already carries no failure value. Audited against the `CLAUDE.md` design list. |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`,
nothing mutable exposed ✓. Module direction unchanged ✓. Koin sole DI, no Hilt/KSP added ✓. Room
untouched — no migration to be destructive ✓. No new network surface ✓. Arabic task content on the
past-day detail keeps `003`'s existing bidirectional handling — the screen is reused, not rebuilt ✓.

**Gate result: PASS.** No violations. Complexity Tracking is empty.

## Spec corrections applied

Three claims in `spec.md` were checked against the merged code. Two held; one needed the spec to be
more precise than it was, and the correction is recorded here rather than silently absorbed.

| Claim checked | Reality |
|---|---|
| "`003` already materialises a plan for an elapsed unopened date in the week it is displaying." | Confirmed — `GetWeekSummary` backfills every elapsed date at or after the record start before aggregating. Unchanged by this increment. |
| "`002` already separated when a completion happened from the day it counts for." | Confirmed — `Completion.recordedAt` and `Completion.creditedDate`. `docs/PLAN.md` assigns this to Phase 5; it shipped in Phase 2. |
| FR-003: the indicator must distinguish outside-the-record and not-yet-elapsed from elapsed-with-nothing. | Already true — `DayCellState` has all five values and `003` renders them. The spec reads as though this needs building; it needs only reusing. No new state is introduced. |

## Project Structure

### Documentation (this feature)

```text
specs/005-history-past-day-review/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── use-cases.md     # GetHistoryPage, GetDayDetail, widened buildWeekSummary
│   └── ui-state.md      # HistoryUiState, DaySummaryUiState changes, navigation
├── checklists/requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

Only additions and the marked changes. Everything else is `002`'s, `003`'s and `004`'s, untouched.

```text
domain/src/
├── main/kotlin/com/giraffe/mizanapp/domain/
│   ├── history/
│   │   ├── HistoryPage.kt                    # NEW — a loaded stretch of weeks + paging bounds
│   │   └── DeriveDayPlan.kt                  # NEW — buildDayPlan, not stored (R1, FR-020a/b)
│   ├── week/
│   │   └── BuildWeekSummary.kt               # CHANGED — projection covers elapsed unplanned dates (R2)
│   └── usecase/
│       ├── GetHistoryPage.kt                 # NEW — a page of weeks, newest first (FR-001..FR-009)
│       └── GetDayDetail.kt                   # NEW — materialise best-effort, then summarise (FR-020c)
└── test/kotlin/com/giraffe/mizanapp/domain/
    ├── week/BuildWeekSummaryElapsedTest.kt   # NEW — elapsed unplanned dates report honestly
    ├── history/DeriveDayPlanTest.kt          # NEW — derived equals stored, field by field (SC-009b)
    └── usecase/
        ├── GetHistoryPageTest.kt             # NEW — continuity, record-start floor, paging
        └── GetDayDetailTest.kt               # NEW — eligibility, best-effort store, no-catalogue

data/src/androidTest/kotlin/com/giraffe/mizanapp/data/
├── CatalogueChangeHistoryTest.kt             # NEW — SC-005, the phase's defining suite
├── HistoryNoWriteTest.kt                     # NEW — SC-008, scrolling changes nothing stored
├── DayOpenMaterialisationTest.kt             # NEW — SC-008a, SC-016a best-effort store
└── HistoryPerformanceTest.kt                 # NEW — SC-015 over three years of seeded data

app/src/
├── main/java/com/giraffe/mizanapp/
│   ├── MainActivity.kt                       # CHANGED — back stack; today routes to Today (R5, R6)
│   ├── di/Modules.kt                         # CHANGED — GetHistoryPage, GetDayDetail, HistoryViewModel
│   ├── history/
│   │   ├── HistoryUiState.kt                 # NEW
│   │   ├── HistoryViewModel.kt               # NEW
│   │   └── HistoryScreen.kt                  # NEW — week rows, paging, empty and failure states
│   ├── daysummary/
│   │   ├── DaySummaryUiState.kt              # CHANGED — + locked-day copy (FR-024)
│   │   └── DaySummaryViewModel.kt            # CHANGED — GetDayDetail instead of GetDaySummary
│   └── week/WeekScreen.kt                    # CHANGED — entry point into history (FR-001)
├── test/java/com/giraffe/mizanapp/
│   ├── history/HistoryViewModelTest.kt       # NEW — paging, floor, empty, failure, retry
│   └── daysummary/DaySummaryViewModelTest.kt # NEW — no-record, derived, locked copy
└── androidTest/java/com/giraffe/mizanapp/
    ├── history/HistoryScreenTest.kt          # NEW — continuity, four cell states, no red (SC-014)
    └── daysummary/DaySummaryScreenTest.kt    # NEW — locked copy, zero rows carry no fault

docs/GLOSSARY.md                              # CHANGED — + Locked Day, + Record Start (R7)
```

**On Compose UI tests**: Principle I exempts only DI wiring, `@Preview` composables, and generated
code. `HistoryScreen` is none of those, so `HistoryScreenTest` precedes it in tasks.md, as `003` and
`004` established.

**Structure Decision**: no new Gradle module. A `history` package in `:domain` holding the page model
and the derivation, two use cases beside `003`'s and `004`'s, and one screen package in `:app`. The
past-day detail is `003`'s `DaySummaryScreen` reached from a second place rather than a new screen —
spec Assumptions rule out building a second, and Principle VIII forbids a duplicate surface for a
projection that already exists.

The one structural judgement is putting **derivation** in `:domain/history/` rather than letting each
caller project what it needs. FR-020b requires a derived day and a stored day to be indistinguishable;
the only way to guarantee that rather than test for it repeatedly is to have both come out of
`buildDayPlan`. The cost is one thin file. See research R1.

## Complexity Tracking

> No constitution violations. This section is intentionally empty.

Two deliberate deviations from `docs/PLAN.md` are recorded rather than hidden, neither a constitution
matter:

- `docs/PLAN.md` names the policy object `DayEditPolicy`; the code has `DayWritePolicy` and every
  write path already consults it. Renaming a correct, consulted policy to match a roadmap sentence
  is risk without gain. One object, one name, one opinion — which is what the roadmap was asking for.
- `docs/PLAN.md` describes this phase as deciding between a retro-completion window and a read-only
  past. It is decided: read-only. The Retro-Completion Window exists as vocabulary with a width of
  zero, so opening it later is a named change to one policy rather than a quiet widening.

## Constitution Re-Check (post-Phase 1 design)

Design introduced five things absent at the first gate. Each re-checked:

| Introduced | Principle at risk | Verdict |
|---|---|---|
| A derived, unstored `DayPlan` carrying a non-identifying id | III, V | **Pass.** It is never inserted, never returned from a repository, and never reaches the sync surface. It exists to be read once and discarded. Principle III is about what a *recorded* day reports, and a derived plan is the honest answer for a date that recorded nothing. |
| `buildWeekSummary`'s widened projection contract | I, III | **Pass.** A pure function with a widened precondition, driven by a test written first. `003`'s existing `BuildWeekSummaryTest` is the regression net and must stay green. Stored plans still win over projection wherever one exists, so no recorded day's figures move. |
| `GetDayDetail` writing on a read path | III, VI | **Pass, and the riskiest change here.** The write is `ensurePlanFor`, which cannot alter an existing plan — it returns `AlreadyExists` untouched. It is bounded to elapsed dates at or after the record start (FR-020), idempotent, and best-effort (FR-020c). SC-008 asserts scrolling writes nothing and SC-008a asserts opening writes exactly one plan. |
| A back stack in `MainActivity` | VIII | **Pass.** Introduced because FR-015 and FR-015a require returning to the originating list, which a single field cannot represent. Still a `List<Destination>` in `rememberSaveable` — no navigation library, no new dependency, no route strings. |
| Today routed to `TodayScreen` from both lists | VI, VII | **Pass, and it removes a hazard rather than adding one.** It makes FR-023's single write surface true at the navigation layer too. No test pins the old routing — it lives only in `MainActivity`, which has no test. |

**Gate result: PASS.** No new violations. Complexity Tracking remains empty.
