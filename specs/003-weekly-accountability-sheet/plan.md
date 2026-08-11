# Implementation Plan: Weekly Accountability Sheet

**Branch**: `spec/003-weekly-accountability-sheet` | **Date**: 2026-08-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-weekly-accountability-sheet/spec.md`

## Summary

The first read model over the completion log. `002` shipped a working single-screen app; this
increment adds a Saturday-to-Friday sheet, backfills the days the app was never opened, and adds a
read-only day summary behind it.

Unlike `002`, this plan extends **running code rather than a blank module tree**. That changes its
character entirely: most of the work is additive queries and one pure aggregate, and the only two
things that touch existing behaviour are a Room migration and a one-line change to how a catalogue
version resolves. Both are called out below because both are the kind of change that damages stored
history if done carelessly.

Three technical decisions carry the increment:

1. **Backfill reuses `DayPlanRepository.ensurePlanFor`.** `002` deliberately wrote that method to
   accept any date, documenting it as Phase 3's entry point. No second creation path exists, so a
   backfilled plan and an opened plan are byte-identical in every field except the new origin
   marker. That is what keeps Principle III honest across two callers.
2. **The sheet is a blocking read that may write.** FR-014a requires final figures at first paint,
   so `GetWeekSummary` performs its backfill before returning. Bounded at seven `ensurePlanFor`
   calls per week viewed.
3. **The week screen arrives without a navigation library.** `002` has one screen and no navigation
   dependency. See research.md R3 — this plan adds no dependency and hoists a small sealed
   destination in `:app`.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged from `002`)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2,
kotlinx.serialization 1.9.0. **No new dependency** — see research.md R3 (navigation) and R5
(migration testing uses `androidx-room-testing`, already in the version catalogue).

**Storage**: Room, offline-only. Schema goes **1 → 2**; migration is additive and non-destructive.
Both schema files exported and committed.

**Testing**: JUnit 4. `:domain` and `:app` on the JVM, `:data` instrumented on device/emulator.
`MigrationTestHelper` for the 1 → 2 migration.

**Target Platform**: Android, `minSdk 24`, `compileSdk 37`, `targetSdk 36`

**Project Type**: Android application, multi-module by layer — `:app` → `:data` → `:domain`

**Performance Goals**: SC-013 — a week whose seven days all need backfilling renders final figures
within 300 ms on a mid-range device; the same for a no-backfill week against a year of records
(~365 plans, ~11k planned tasks, ~18k completions). Worst case per week open: 1 range query over
`day_plans`, 1 over `completions`, and at most 7 `ensurePlanFor` calls.

**Constraints**: `:domain` keeps zero Android on its classpath. No plan may ever be updated. No
network. Backfill must be idempotent under concurrent week opens.

**Scale/Scope**: 2 new screens, 1 Room migration, 1 new domain package (`week`), ~45 functional
requirements. No new module.

## What `002` already provides

Verified against the merged code on `develop-v1`, not against `002`'s documents. This matters —
two assumptions in the spec's first draft were wrong and are corrected in [Spec corrections](#spec-corrections-applied).

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Create a plan for an arbitrary past date | ✅ `DayPlanRepository.ensurePlanFor(date)` — accepts any date, documented as Phase 3's backfill entry point |
| Resolve the catalogue version for a past date | ✅ `CatalogueRepository.versionEffectiveOn(date)`, backed by `catalogue_versions.effectiveFrom` from `001` — **but returns null before the earliest version** (changed here, R1) |
| Per-day earned/available | ✅ `scoreDay(plan, completions)` in `:domain` |
| Frozen plan, no update path | ✅ `DayPlanDao` has no update method; `DayPlanRepository` cannot express one |
| Injected clock | ✅ `TimeProvider`, `DayBoundary` |
| Single day-writability rule | ✅ `DayWritePolicy` — consulted by `CompletionRepository` only |
| Hijri label per plan | ✅ stored on `DayPlanEntity`, computed locally |
| Week boundary rule | ❌ does not exist — new here |
| Plans/completions over a date **range** | ❌ every query is single-date — new DAO methods here |
| Earliest recorded date | ❌ new here |
| How a plan came into being | ❌ new column, migration 1 → 2 |
| Navigation between screens | ❌ `MainActivity` hosts `TodayRoute` directly — R3 |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer: `:domain` week-boundary and aggregate tests → domain code → `:data` migration and range-query tests → data code → ViewModel tests → **Compose UI tests → screens**. The migration test is written before the migration. Only Koin wiring and `@Preview` claim the exemption — screens do **not**, so all four screen tasks are preceded by a UI test task in `app/src/androidTest`. |
| **II — Domain purity** | Yes | `WeekCalculator`, `Week`, `WeeklyScore`, `GetWeekSummary` are pure Kotlin in `:domain`, which is a `kotlin("jvm")` module — an Android import cannot compile there. Backfill *orchestration* needs a repository, so it is a use case over interfaces, not a repository call from a composable. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | No update path is added to `DayPlanDao` or `DayPlanRepository`; the origin column is written at insert only. The migration is additive with a constant default, so no stored figure moves. FR-013 makes backfill use the catalogue version effective on the backfilled date, not the current one. A `:data` test bumps the catalogue and asserts stored days — opened **and** backfilled — are unchanged. |
| **IV — Offline-first** | Yes | No network. Every figure comes from Room. The sheet and day summary work on a fresh install in airplane mode. |
| **V — Backend independence** | Yes | The new column sits on an already-synchronisable row that carries UUID id, `updatedAt`, `deletedAt`, `userId`. Origin is a fact about the record, so it syncs with it. No new repository interface shape that a backend would have to change. |
| **VI — Fixed content** | Yes | The whole feature is a read. No create/edit/delete/reorder affordance exists on either screen, and no event in either UI state can express one. Backfill writes plans, never completions (FR-011a). |
| **VII — Deterministic time** | Yes | `WeekCalculator` is the single Saturday-to-Friday rule, alongside the existing `DayBoundary`. Nothing else computes a week. No new code reads a clock — `TimeProvider` stays the only source. Week rollover is tested with a fake clock. |
| **VIII — Vertical slices** | Yes | Four stories shipping as one usable capability. No `DaySummary` cache table (spec Assumptions, `docs/PLAN.md`). No navigation library for two screens (R3). No streak, chart, or history surface. |
| **IX — Encouragement** | Yes | FR-016 and FR-017a: never-elapsed, outside-the-record, and zero-earned are three visually distinct neutral states, none red, none a cross. FR-009a keeps the mid-week denominator to elapsed days so a Sunday cannot read as 10% of a week. FR-014c bars blaming the user for a storage failure. Audited against the `CLAUDE.md` design list. |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`,
nothing mutable exposed ✓. Module direction unchanged ✓. Koin sole DI, no Hilt/KSP added ✓. Room
with exported schemas, migration non-destructive ✓. No new network surface ✓. Arabic task content
rendered as data with correct bidirectional handling on both new screens ✓.

**Gate result: PASS.** No violations. Complexity Tracking is empty.

## Spec corrections applied

Two claims in the spec were checked against the merged code and were wrong. Both are corrected in
`spec.md`; neither changes a decision the author made.

| Claim | Reality | Effect |
|---|---|---|
| "Catalogue version effective dates are new storage… `002` does not record from when." | `001` defined `effectiveFrom` per version and validated its ordering; `002` persists it in `catalogue_versions` and already ships `versionEffectiveOn(date)`. | FR-013a is a resolution rule, not a schema change. The increment is **smaller** than specified. |
| Implied: the clarified "open-ended backwards" rule is satisfied by existing behaviour. | `versionEffectiveOn` returns **null** for any date before the earliest version's `effectiveFrom` — the seed ships `2026-01-01`. | Real change needed (R1). Without it, FR-013b is unimplemented and the record-start floor is not the only floor, which is precisely what the clarification chose to avoid. |

The Day Plan origin field is unaffected — it is genuinely new, and it is the only schema change here.

## Project Structure

### Documentation (this feature)

```text
specs/003-weekly-accountability-sheet/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── repositories.md  # new + changed repository surface
│   └── ui-state.md      # WeekUiState, DaySummaryUiState
├── checklists/requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

Only additions and the two marked changes. Everything else in the tree is `002`'s and untouched.

```text
domain/src/
├── main/kotlin/com/giraffe/mizanapp/domain/
│   ├── time/
│   │   └── WeekBoundary.kt              # NEW — Sat→Fri, the single week rule (FR-001)
│   ├── week/
│   │   ├── Week.kt                      # NEW — seven dates + WeekKey (FR-002)
│   │   ├── WeeklyScore.kt               # NEW — earned / elapsedAvailable / weekTarget
│   │   ├── DayCellState.kt              # NEW — five neutral day states (FR-015)
│   │   ├── WeekSummary.kt               # NEW — Week + WeeklyScore + seven DayCells
│   │   ├── ProjectAvailablePoints.kt    # NEW — future days, computed, never persisted (FR-009d)
│   │   ├── DaySummary.kt                # NEW — one date's read-only projection
│   │   └── BuildWeekSummary.kt          # NEW — pure fold over plans + completions
│   ├── day/
│   │   ├── DayPlan.kt                   # CHANGED — + origin: PlanOrigin
│   │   ├── PlanOrigin.kt                # NEW — OPENED | BACKFILLED
│   │   └── BuildDayPlan.kt              # CHANGED — + origin parameter
│   ├── repository/
│   │   ├── CatalogueRepository.kt       # CHANGED — versionEffectiveOn doc/semantics (R1)
│   │   ├── CompletionRepository.kt      # CHANGED — + liveBetween
│   │   └── DayPlanRepository.kt         # CHANGED — + plansBetween, earliestPlanDate
│   └── usecase/
│       ├── GetWeekSummary.kt            # NEW — backfill then aggregate; the only orchestrator
│       └── GetDaySummary.kt             # NEW — one date, read-only, no creation path
└── test/kotlin/…                        # week boundary, aggregate, backfill-policy tests first

data/
├── schemas/
│   ├── 1.json                           # existing, untouched
│   └── 2.json                           # NEW — exported, committed
└── src/
    ├── main/kotlin/com/giraffe/mizanapp/data/
    │   ├── db/
    │   │   ├── MizanDatabase.kt         # CHANGED — version = 2
    │   │   ├── MizanDatabaseFactory.kt  # CHANGED — .addMigrations(MIGRATION_1_2)
    │   │   ├── Migrations.kt            # NEW — additive ALTER TABLE, no data loss
    │   │   ├── entities/DayEntities.kt  # CHANGED — + origin column
    │   │   └── daos/
    │   │       ├── DayPlanDao.kt        # CHANGED — + range + earliest queries
    │   │       └── CompletionDao.kt     # CHANGED — + live-in-range query
    │   ├── mapper/Mappers.kt            # CHANGED — origin both directions
    │   └── repository/
    │       ├── RoomDayPlanRepository.kt # CHANGED — writes origin; range reads
    │       └── RoomCatalogueRepository.kt # CHANGED — open-ended earliest version (R1)
    └── androidTest/kotlin/…             # migration test, range queries, immutability across backfill

app/src/main/java/com/giraffe/mizanapp/
├── MainActivity.kt                      # CHANGED — hosts a destination, not a screen (R3)
├── di/Modules.kt                        # CHANGED — GetWeekSummary + two ViewModels
├── today/TodayScreen.kt                 # CHANGED — one header action opening the week
├── week/                                # NEW
│   ├── WeekViewModel.kt  WeekUiState.kt  WeekScreen.kt
└── daysummary/                          # NEW
    ├── DaySummaryViewModel.kt  DaySummaryUiState.kt  DaySummaryScreen.kt

app/src/androidTest/java/com/giraffe/mizanapp/    # NEW — Compose UI tests
├── week/  WeekScreenTest.kt  WeekFailureStateTest.kt  WeekNavigationScreenTest.kt
└── daysummary/  DaySummaryScreenTest.kt
```

**On Compose UI tests**: Principle I exempts only DI wiring, `@Preview` composables, and generated
code. Screens are not exempt, so each screen task in tasks.md is preceded by a UI test task.
`androidx-compose-ui-test-junit4` and `ui-test-manifest` are already declared in `app/build.gradle.kts`,
so this needs no build change. `002` shipped `TodayScreen.kt` without one — that is a gap in `002`,
not a precedent this increment may cite.

**Structure Decision**: no new Gradle module. The three-module split from `002` already holds this
feature — a `week` package in `:domain`, additive queries in `:data`, two screens in `:app`. A
`:feature:week` module would add build configuration to enforce a boundary that the package
structure and `:domain`'s purity already enforce, which Principle VIII rules out.

The one structural judgement is placing `GetWeekSummary` in `:domain/usecase/`. It is the first use
case in the codebase that both writes and reads — it triggers backfill, then aggregates. It stays
pure of frameworks by depending only on the two repository interfaces, and it exists so that neither
the ViewModel nor a repository holds the "display a week" rule. Putting the backfill loop in
`WeekViewModel` would place a Principle III-critical rule in `:app`, where no test-first discipline
for domain rules applies and where a second screen could later disagree with it.

## Constitution Re-Check (post-Phase 1 design)

Design introduced four things absent at the first gate. Each re-checked:

| Introduced | Principle at risk | Verdict |
|---|---|---|
| `PlanOrigin` column + migration 1 → 2 | III | **Pass.** Additive with a constant default; the migration writes `'OPENED'` to every existing row, which is not a guess — `002` creates a plan only on launch for the current date, so no existing plan can be a backfill (FR-013e). No stored figure is touched. `MigrationTestHelper` asserts a v1 database's plans and completions survive with identical values. |
| `GetWeekSummary` writes during a read | III, VI | **Pass.** It writes only through `ensurePlanFor`, which cannot overwrite (FR-010b, enforced by `OnConflictStrategy.ABORT` plus a pre-check). It never writes a completion (FR-011a). VI is untouched: creating the day that already happened is not authoring content. |
| Earliest catalogue version made open-ended | III | **Pass, and required.** It cannot re-score anything: it only makes a plan *creatable* for a date that previously resolved to no catalogue. Existing plans are never re-resolved. Without it FR-013b has no implementation. Changes `002`'s contract wording — recorded in research.md R1. |
| Hand-rolled destination in `:app` (no nav library) | VIII | **Pass.** Two screens and one back action. A navigation dependency would be introduced for a capability that is three lines of state, and Principle VIII forbids abstraction for a need that is not present. R3 records the point at which that stops being true. |

**Gate result: PASS.** No new violations. Complexity Tracking remains empty.

## Complexity Tracking

> No constitution violations and no deviation from `docs/PLAN.md`. This section is intentionally
> empty.

`docs/PLAN.md` Phase 3 states "No new tables strictly required" and defers a `DaySummary` cache
until measurement demands it. This plan adds no table and no cache, and adds one column that the
roadmap did not anticipate — for a reason the roadmap itself creates, since Phase 4's streak rule
needs to distinguish presence from a backfilled plan. That is an addition the roadmap invites rather
than a departure from it.
