# Implementation Plan: Charts & Insights

**Branch**: `spec/006-charts-insights` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-charts-insights/spec.md`

## Summary

The first purely read-model feature past the MVP: three views over data that `002`–`005` already
made trustworthy — a weekly completion trend, a monthly day-cell overview, and a per-section
breakdown with personal bests. **No table, no column, no migration, no new write path anywhere.**

Four technical decisions carry it:

1. **The weekly trend is `GetHistoryPage`, reordered.** `005`'s `GetHistoryPage` already returns
   backfill-free, no-write `WeekSummary` pages newest-first, floored at the record start. A new
   `GetWeeklyTrend` use case calls it with `weeksPerPage = 8` (the clarified default) and reverses
   the list to oldest-first for charting. No second week-aggregation implementation is written. See
   research R1.
2. **`buildWeekSummary`'s day-cell logic is extracted, not duplicated.** The function that turns a
   list of dates plus stored/projected data into `DayCell`s is week-shaped only because its one
   caller wanted seven dates. Pulling it out as `buildDayCells(dates, ...)` lets the monthly overview
   reuse it for an arbitrary calendar month with zero behavioural change to `buildWeekSummary` itself
   — a pure refactor that unlocks User Story 2, satisfying Principle VIII's own test for when a
   refactor is allowed. See research R2.
3. **The four day-cell states already satisfy the Q1 clarification exactly.** `DayCellState` —
   `OUTSIDE_RECORD` / `NOTHING_RECORDED` / `PARTLY_RECORDED` / `FULLY_RECORDED`, plus
   `NOT_YET_ELAPSED` — already carries the no-data / untouched / partial / complete bands the
   clarification asked for, already mapped to non-red containers on `WeekScreen`. The monthly
   overview reuses the type and the color mapping instead of inventing a second one. See research R3.
- Applies to the color mapping too: it moves to a small shared file so `WeekScreen` and the new
   `InsightsScreen` read one definition, not two.
4. **Section breakdown and personal bests read the same range once, never per-chart.** Both need
   every stored-or-derived plan and every live completion across a period; `InsightsViewModel` loads
   the period's plans and completions with the existing range reads (`plansBetween`, `liveBetween`)
   once per period change and derives all three charts from that one payload plus `GetWeeklyTrend`
   and `GetPersonalBests`' own (separately bounded) full-record read. See research R4.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11 (unchanged)

**Primary Dependencies**: Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01, coroutines 1.10.2. **No new
dependency.** Charts are plain Compose (`Canvas`/`Row`/grid layouts) over already-loaded domain
models — no charting library, consistent with the roadmap's "chart library confined to the UI layer,
domain returns chart-agnostic models" and with not needing one yet.

**Storage**: Room, offline-only. **No schema change.** Database stays at its current version — every
read here already exists (`plansBetween`, `liveBetween`, `earliestPlanDate`, `versionEffectiveOn`,
`catalogueAt`). No new DAO method.

**Testing**: JUnit 4. `:domain` and `:app` on the JVM, `:data` instrumented on device/emulator for the
performance and no-write suites. Compose UI tests for each Insights view and the color/no-red audit.

**Target Platform**: Android, `minSdk 24`, `compileSdk 36`, `targetSdk 36`

**Performance Goals**: SC-002 — each of the three views renders within 1 second with a full year of
recorded history (~365 plans, ~365×~15 planned-task rows, tens of thousands of completions).
`GetWeeklyTrend` reads 8 weeks (56 dates, same shape as one `GetHistoryPage` page — already
benchmarked in `005`). `GetMonthOverview` reads at most 31 dates. `GetSectionBreakdown` reads at
most one month or one week. `GetPersonalBests` is the one full-record read and gets its own
instrumented performance test against a 3-year fixture, mirroring `005`'s `HistoryPerformanceTest`.

**Constraints**: `:domain` keeps zero Android on its classpath. No chart, filter, period switch, or
drill-in writes anything (FR-009). No network. Every color used is a shade of the existing primary
green — no red anywhere (FR-010, SC-006).

**Scale/Scope**: 1 new screen (three switchable views + a personal-bests card), 0 migrations, 0 new
DAO methods, 1 new domain package (`insights`), 4 new use cases, 1 extracted pure function
(`buildDayCells`), 11 functional requirements.

## What `002`–`005` already provide

Verified against the merged code on `develop-v1`, not against their documents.

| Needed by this feature | Status in `develop-v1` |
|---|---|
| Backfill-free, no-write weekly pages, newest-first, floored at record start | ✅ `GetHistoryPage` (`005`) |
| Arbitrary-range reads of plans and completions | ✅ `plansBetween(start, end)`, `liveBetween(start, end)` |
| Record start | ✅ `DayPlanRepository.earliestPlanDate()` |
| The version that applied on a past date | ✅ `CatalogueRepository.versionEffectiveOn(date)` |
| Deriving a plan for an arbitrary date without storing it | ✅ `deriveDayPlan(catalogue, version, date)` (`005`) |
| Availability without persisting, for future dates | ✅ `projectAvailablePoints(catalogue, version, date)` |
| Four-plus-one neutral day states, already color-mapped | ✅ `DayCellState`, `WeekScreen.containerColorFor` |
| A denominator that never blames an unfinished period | ✅ `WeeklyScore.elapsedAvailable` vs `weekTarget` — exactly what FR-008/Q3's "in-progress period" distinction needs, already proven on `WeekScreen` |
| One week rule | ✅ `WeekBoundary` |
| A read-only screen pattern (`Loading`/`RecordNotStarted`/`CouldNotLoad`/`CatalogueUnavailable`/`Ready`) | ✅ `HistoryUiState` (`005`) — reused as the shape for `InsightsUiState` |
| Per-date `DayCell`s from a list of dates | ❌ `buildWeekSummary` inlines this for exactly seven dates — R2 |
| A month-shaped read | ❌ nothing reads a calendar month today — new |
| A per-section rate across a period | ❌ nothing aggregates by section today — new |
| A full-record best-day/best-week scan | ❌ nothing scans the whole record today — new |
| A navigation entry point to Insights | ❌ `MainActivity`'s `Destination` has no `Insights` case — new |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.1.1.

| Principle | Touched | Compliance |
|---|---|---|
| **I — Test-first (NON-NEGOTIABLE)** | Yes | Order per layer: `:domain` pure tests (`buildDayCells` extraction equivalence, `buildMonthOverview`, `buildSectionBreakdown`, `buildPersonalBests`) → domain code → use-case tests → use cases → `:data` instrumented tests (no-write, performance) → ViewModel tests → ViewModel → **Compose UI tests → `InsightsScreen`**. Only Koin wiring and `@Preview` claim the exemption. |
| **II — Domain purity** | Yes | `insights/` is Kotlin in `:domain`. `GetWeeklyTrend`, `GetMonthOverview`, `GetSectionBreakdown`, `GetPersonalBests` depend only on repository interfaces and `TimeProvider`, same as every existing use case. |
| **III — Immutable history (NON-NEGOTIABLE)** | Yes | Every chart value is read from a stored `DayPlan`/`Completion` or, for a date never opened, derived read-only with `versionEffectiveOn` — never from the live catalogue (FR-006). No write path exists anywhere in this feature (FR-009) — there is no insert, update, or delete in any new use case, mirroring `GetHistoryPage`'s own no-write guarantee. SC-003's catalogue-change-mid-period test is this feature's version of `005`'s defining suite. |
| **IV — Offline-first** | Yes | No network. Every figure comes from Room plus the seeded catalogue, already loaded. |
| **V — Backend independence** | Yes | No new row, no new column, no new identifier, no repository interface change. Pure read models only. |
| **VI — Fixed content** | Yes | Read-only end to end — `InsightsUiState` carries no event that can create, edit, delete, reorder, or reprice anything, same shape as `005`'s `HistoryUiState`. |
| **VII — Deterministic time** | Yes | `TimeProvider` stays the only clock; `WeekBoundary` stays the only week rule. The month overview uses `YearMonth` from `java.time` purely as a date-range descriptor, never as a second boundary rule — "month" here is a display grouping, not an accountability boundary. |
| **VIII — Vertical slices** | Yes | One coherent capability: open Insights, see the trend, the month, the sections, and your bests. No prediction, no AI summary, no goal-setting, no export/share, no social comparison — all explicitly out of scope per spec. The `buildDayCells` extraction is the plan's one refactor, and it is justified because User Story 2 needs exactly the capability it unlocks, named here. |
| **IX — Encouragement** | Yes | The clarified design is deliberately conservative: discrete non-red bands reused from `WeekScreen`, personal bests show only the high point (no "worst"), and the section breakdown lists every section in catalogue order with no ranking, sort-by-rate, or lowest-section badge (Clarifications Q2). Audited against the `CLAUDE.md` design list before this feature is considered done (SC-006). |

**Technology constraints**: Kotlin + Compose ✓. MVVM, one immutable state per screen as `StateFlow`
✓. Module direction unchanged ✓. Koin sole DI, no Hilt/KSP added ✓. Room untouched — no migration to
be destructive ✓. No new network surface ✓. No chart library added — charts stay swappable Compose
primitives behind the screen boundary per the roadmap's own deferred-decision list ✓.

**Gate result: PASS.** No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/006-charts-insights/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/            # Phase 1 output
│   ├── use-cases.md     # GetWeeklyTrend, GetMonthOverview, GetSectionBreakdown, GetPersonalBests
│   └── ui-state.md      # InsightsUiState, InsightsEvent, navigation entry point
├── checklists/requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

Only additions and the marked changes. Everything else is `002`'s, `003`'s, `004`'s and `005`'s,
untouched.

```text
domain/src/
├── main/kotlin/com/giraffe/mizanapp/domain/
│   ├── week/
│   │   └── BuildWeekSummary.kt               # CHANGED — day-cell loop extracted to buildDayCells (R2)
│   ├── insights/
│   │   ├── SectionPerformance.kt             # NEW — sectionId, sectionLabel, completed, available
│   │   ├── MonthOverview.kt                  # NEW — YearMonth + List<DayCell> (reuses week.DayCell)
│   │   ├── PersonalBests.kt                  # NEW — bestDay: DayCell?, bestWeek: WeekSummary?
│   │   ├── BuildMonthOverview.kt             # NEW — pure, calls buildDayCells over a month's dates
│   │   ├── BuildSectionBreakdown.kt          # NEW — pure, groups planned-task occurrences by section
│   │   └── BuildPersonalBests.kt             # NEW — pure, scans a full-record day-cell/week payload
│   └── usecase/
│       ├── GetWeeklyTrend.kt                 # NEW — wraps GetHistoryPage(weeksPerPage=8), reversed
│       ├── GetMonthOverview.kt               # NEW — no-write month read (FR-002, FR-006, FR-007)
│       ├── GetSectionBreakdown.kt            # NEW — no-write period read (FR-003)
│       └── GetPersonalBests.kt               # NEW — no-write full-record read (FR-004)
└── test/kotlin/com/giraffe/mizanapp/domain/
    ├── week/BuildWeekSummaryTest.kt          # CHANGED — asserts unchanged behaviour post-extraction
    └── insights/
        ├── BuildDayCellsTest.kt              # NEW — the extracted function, independent of week shape
        ├── BuildMonthOverviewTest.kt         # NEW — full month, sparse month, pre-install month, DST/year-boundary month
        ├── BuildSectionBreakdownTest.kt      # NEW — uneven section completion, mid-period catalogue change
        ├── BuildPersonalBestsTest.kt         # NEW — single-day history, uniform history, tie handling
        └── usecase/
            ├── GetWeeklyTrendTest.kt         # NEW — 8-week default, shorter-than-8-weeks history, in-progress week
            ├── GetMonthOverviewTest.kt       # NEW — month/year boundary crossings, before-install dates
            ├── GetSectionBreakdownTest.kt    # NEW — week period, month period, no-catalogue outcome
            └── GetPersonalBestsTest.kt       # NEW — record-start floor, no-history empty state

data/src/androidTest/kotlin/com/giraffe/mizanapp/data/
├── InsightsNoWriteTest.kt                    # NEW — SC-001/FR-009, opening/navigating Insights writes nothing
├── InsightsCatalogueChangeTest.kt            # NEW — SC-003, mid-period catalogue version change
└── InsightsPerformanceTest.kt                # NEW — SC-002, three views + personal bests over a year/3 years

app/src/
├── main/java/com/giraffe/mizanapp/
│   ├── MainActivity.kt                       # CHANGED — + Destination.Insights, entry point wiring
│   ├── di/Modules.kt                         # CHANGED — 4 new use cases, InsightsViewModel
│   ├── ui/
│   │   └── DayCellColors.kt                  # NEW — DayCellState → color, extracted from WeekScreen (shared)
│   ├── week/WeekScreen.kt                    # CHANGED — uses shared DayCellColors; + OpenInsights entry point
│   └── insights/
│       ├── InsightsUiState.kt                # NEW
│       ├── InsightsViewModel.kt              # NEW
│       └── InsightsScreen.kt                 # NEW — trend / month / sections switcher + personal-best card
├── test/java/com/giraffe/mizanapp/
│   └── insights/InsightsViewModelTest.kt     # NEW — view switching, period navigation, all Ready/failure states
└── androidTest/java/com/giraffe/mizanapp/
    └── insights/InsightsScreenTest.kt        # NEW — three views render, sparse/1-day/365-day data, no-red audit
```

**On Compose UI tests**: Principle I exempts only DI wiring, `@Preview` composables, and generated
code. `InsightsScreen` is none of those, so `InsightsScreenTest` precedes it in tasks.md, as `003`,
`004` and `005` established.

**Structure Decision**: no new Gradle module. One `insights` package in `:domain` holding four small
pure functions and their result types, four use cases beside the existing ones, and one screen
package in `:app`. The navigation entry point is a button on `WeekScreen` next to the existing
`OpenHistory` button (mirroring `005`'s own single entry point into History) — the roadmap's
three-tab shell with Insights living under "Progress" is a later, separate concern (spec Assumptions)
and is not spun up speculatively here (Principle VIII).

## Complexity Tracking

*No violations — table intentionally empty.*
