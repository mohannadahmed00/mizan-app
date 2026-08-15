---
description: "Task list for Charts & Insights (006)"
---

# Tasks: Charts & Insights

**Input**: Design documents from `specs/006-charts-insights/` — `plan.md`, `spec.md`, `research.md`,
`data-model.md`, `contracts/use-cases.md`, `contracts/ui-state.md`, `quickstart.md`.

**Tests**: **NOT optional in this project.** Constitution Principle I (test-first, non-negotiable):
every task below that adds behavior has its test task listed immediately before it, and that test
MUST be written, run, and observed to FAIL for the right reason before the implementation task
starts. The one exception per the constitution is Koin DI wiring — those tasks have no preceding
test task and are marked as such.

**Read this before starting any task**: `specs/006-charts-insights/plan.md` (architecture decisions
1–4 and the Constitution Check), `specs/006-charts-insights/research.md` (R1–R4, why each reuse
decision was made instead of writing new code), `specs/006-charts-insights/data-model.md` (exact
type shapes), `specs/006-charts-insights/contracts/use-cases.md` and `contracts/ui-state.md` (exact
function signatures and behavior). Every task below names the exact section of these files that
defines what it must do — open that section before writing any code for the task.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel — different files, no dependency on an incomplete task in this list.
- **[Story]**: US1 (weekly trend), US2 (monthly overview), US3 (sections + personal bests). Setup,
  Foundational, and Polish tasks carry no story label.
- Every task names the exact file(s) it creates or edits.

## Path Conventions

This is a 3-module Android project: `domain/src/main/kotlin/com/giraffe/mizanapp/domain/`,
`domain/src/test/kotlin/com/giraffe/mizanapp/domain/`, `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/`,
`app/src/main/java/com/giraffe/mizanapp/`, `app/src/test/java/com/giraffe/mizanapp/`,
`app/src/androidTest/java/com/giraffe/mizanapp/`. All paths below are relative to the repository
root. No new Gradle module, no new dependency, no schema migration in this feature.

---

## Phase 1: Setup

**Purpose**: Read the existing code this feature reuses, before writing anything new. There is no
project initialization needed — no new module, dependency, or schema change (plan.md "Technical
Context").

- [X] T001 [P] Read these five files in full before starting any other task, so the reuse decisions
  in `research.md` R1–R3 make sense: `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetHistoryPage.kt`,
  `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt`,
  `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/DayCellState.kt`,
  `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`,
  `app/src/main/java/com/giraffe/mizanapp/history/HistoryUiState.kt`. No file is changed by this
  task — it is a reading task, done once.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared plumbing every user story's implementation task touches: the extracted
`buildDayCells` function (needed by US2 and US3), the shared color mapping, the navigation entry
point, and the shape of `InsightsUiState`/`InsightsEvent`. **No user story task may start before
this phase is checked off.**

- [X] T002 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildDayCellsTest.kt`.
  This test does not exist yet — write it against the signature in `data-model.md` under "Extracted
  pure function": `buildDayCells(dates, today, recordStart, plans, completions, projectedAvailable):
  List<DayCell>`. Cover every branch `buildWeekSummary`'s current inline loop has (read
  `BuildWeekSummary.kt` first, per T001): a date after `today` → `NOT_YET_ELAPSED`; a date before
  `recordStart` (or `recordStart == null`) → `OUTSIDE_RECORD`; a date with a stored plan and 0 earned
  → `NOTHING_RECORDED`; earned equal to the plan's `availablePoints` → `FULLY_RECORDED`; earned
  between 0 and available → `PARTLY_RECORDED`; a date with no stored plan that falls in
  `projectedAvailable` → its `available` comes from the map, not from a plan. Run it — it MUST fail
  to compile (the function does not exist yet). That failure is the expected state before T003.

- [X] T003 Extract `buildDayCells` from `buildWeekSummary` in
  `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt`. Move the per-date
  `week.dates.map { ... }` loop (the block computing `plan`, `earned`, `state`, `available`, and
  building each `DayCell`) into a new top-level function `buildDayCells(dates: List<LocalDate>,
  today: LocalDate, recordStart: LocalDate?, plans: List<DayPlan>, completions: List<Completion>,
  projectedAvailable: Map<LocalDate, Int>): List<DayCell>` in the same file, taking `dates` instead
  of reading `week.dates` directly. Change `buildWeekSummary` to call
  `buildDayCells(week.dates, today, recordStart, plans, completions, projectedAvailable)` for its
  `days` variable, then keep computing `elapsedAvailable`/`futureAvailable`/`weekTarget`/`earned`/
  `WeeklyScore` exactly as it does today from the returned list. Do not change
  `buildWeekSummary`'s public signature or behavior in any way.
  **Verify**: run `./gradlew :domain:test`. T002's new test must now pass, AND the existing
  `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummaryTest.kt` and
  `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummaryTest.kt` (if present)
  must still pass **unmodified** — do not edit those test files. If either fails, the extraction
  changed behavior; fix `buildDayCells`, not the old tests.

- [X] T004 Create `app/src/main/java/com/giraffe/mizanapp/ui/DayCellColors.kt` with a single
  `@Composable fun containerColorFor(state: DayCellState): Color`. Copy the body of the existing
  private `containerColorFor` function from `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`
  (found via T001) verbatim — same five `DayCellState` branches, same `MaterialTheme.colorScheme`
  values, no new color introduced. This is a pure move, not a rewrite: no new test is required
  because the existing `app/src/androidTest/java/com/giraffe/mizanapp/week/WeekScreenTest.kt` already
  exercises every branch and must still pass after T005.

- [X] T005 In `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`: delete the private
  `containerColorFor` function and replace every call site with
  `com.giraffe.mizanapp.ui.DayCellColors.containerColorFor(...)` (or an import + bare call —
  match the existing import style in the file). **Verify**: run
  `./gradlew :app:connectedAndroidTest --tests "*WeekScreenTest*"` and confirm it still passes
  unmodified.

- [X] T006 In `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`: add `data object Insights :
  Destination` to the `sealed interface Destination` block; add `Destination.Insights -> "INSIGHTS"`
  to the `encode` function; add `encoded == "INSIGHTS" -> Destination.Insights` to the `decode`
  function (same pattern as the existing `Destination.History` / `"HISTORY"` cases — read them
  first). Do not wire a screen composable for it yet — that lands in T016.

- [X] T007 In `app/src/main/java/com/giraffe/mizanapp/week/WeekUiState.kt`: add
  `data object OpenInsights : WeekEvent` to the `sealed interface WeekEvent` block, next to the
  existing `OpenHistory` case. In `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`: add a
  second button next to the existing "History" button (the one that calls
  `onEvent(WeekEvent.OpenHistory)`, found via T001) that calls `onEvent(WeekEvent.OpenInsights)`,
  labelled "Insights". In `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`: wherever
  `WeekEvent.OpenHistory` is currently handled (pushes `Destination.History` onto the back stack),
  add the matching handling for `WeekEvent.OpenInsights` pushing `Destination.Insights`.

- [X] T008 [P] Create `app/src/main/java/com/giraffe/mizanapp/insights/InsightsUiState.kt`. Copy the
  full `InsightsUiState` (including its `trendHasMore: Boolean = false` and
  `isLoadingEarlierTrend: Boolean = false` fields), `Status` sealed interface, `InsightsView` enum,
  `TrendPointUi`, `MonthOverviewUi`, `SectionRowUi`, `PersonalBestsUi`, `BestDayUi`, and `BestWeekUi`
  data classes exactly as specified in `specs/006-charts-insights/contracts/ui-state.md` under
  `## InsightsUiState`. Import `WeekKey` from `com.giraffe.mizanapp.domain.week.WeekKey` and
  `DayCellUi` from `com.giraffe.mizanapp.week.DayCellUi` (the existing type — do not create a new
  one, per research R3). This file will not compile against a real `InsightsViewModel` yet; that is
  expected until Phase 3.

- [X] T009 [P] Create `app/src/main/java/com/giraffe/mizanapp/insights/InsightsEvent.kt` with the
  `sealed interface InsightsEvent` exactly as specified in `contracts/ui-state.md` under
  `## InsightsEvent`: `SelectView(view: InsightsView)`, `LoadEarlierTrend`, `PreviousMonth`,
  `NextMonth`, `SwitchSectionPeriod(toMonth: Boolean)`, `Retry`. `LoadEarlierTrend` mirrors
  `HistoryEvent.LoadMore` (`005`, found via T001) — it is what makes User Story 1 Acceptance
  Scenario 3 (scrolling the trend back to the install week) implementable; do not skip it.

- [X] T010 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/InsightsPeriod.kt`
  with the `sealed interface InsightsPeriod` exactly as specified in `data-model.md` under
  "Period selection": `ForWeek(week: Week)`, `ForMonth(month: YearMonth)`.

**Checkpoint**: `./gradlew :domain:test` and the WeekScreen instrumented test both pass. A working
"Insights" button exists on the Week screen and navigates to a not-yet-implemented screen (that
screen composable is built in T016; until then `MainActivity` can route `Destination.Insights` to a
placeholder or you may defer wiring the actual `composable` call until T016 — either is fine, this
phase does not require the screen to render).

---

## Phase 3: User Story 1 - Weekly consistency trend (Priority: P1) 🎯 MVP

**Goal**: Opening Insights shows a bar/point per recent week (default: last 8, per Clarifications)
with that week's completion percentage, matching what the Week Screen itself would show for the same
week, with the current in-progress week visually distinguishable from a completed one.

**Independent Test**: Seed 4 completed past weeks with mixed completion, open Insights (default view
is Trend), verify one point per week with the correct percentage; seed only 2 days of history and
verify the single in-progress week renders distinctly rather than reading as a bad week.

### Tests for User Story 1 ⚠️ MUST be written and FAIL before implementation

- [X] T011 [P] [US1] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeeklyTrendTest.kt`.
  Signature under test (`contracts/use-cases.md` → `## GetWeeklyTrend`):
  `class GetWeeklyTrend(private val historyPage: GetHistoryPage) { suspend operator fun
  invoke(before: WeekKey? = null, weeks: Int = 8): TrendOutcome }` where `TrendOutcome` is
  `Ready(weeks: List<WeekSummary>, hasMore: Boolean)` / `NoHistory` /
  `CatalogueUnavailable(detail: String)`. Use a fake or mock `GetHistoryPage` (it already has its own
  tests in `005` — do not re-test its internals here, only that `GetWeeklyTrend` calls it correctly).
  Cases: (a) called with `before = null` → delegates to `historyPage(before = null, weeksPerPage =
  8)`; a returned page of 8 weeks newest-first with `hasMore = true` → `GetWeeklyTrend` returns
  `Ready` with those 8 weeks **reversed to oldest-first** and `hasMore = true`; (b) history shorter
  than 8 weeks → `Ready` with however many weeks exist, still oldest-first, `hasMore = false`; (c)
  called with `before = someWeekKey` → delegates to `historyPage(before = someWeekKey, weeksPerPage =
  8)`, exercising the scroll-back path (US1 Acceptance Scenario 3); (d) the delegated call returns
  `hasMore = false` (the record-start week was reached) → `TrendOutcome.Ready.hasMore` is `false`,
  regardless of `before`; (e) `HistoryOutcome.Ready` with an empty `weeks` list (record never
  started) → `NoHistory`; (f) `HistoryOutcome.CatalogueUnavailable` →
  `TrendOutcome.CatalogueUnavailable` with the same detail string passed through. Run it — it MUST
  fail (the class does not exist).

### Implementation for User Story 1

- [X] T012 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeeklyTrend.kt`
  exactly per T011's signature and `contracts/use-cases.md` → `## GetWeeklyTrend`. **Verify**: run
  `./gradlew :domain:test`, T011 now passes.

- [X] T013 [P] [US1] Write `app/src/test/java/com/giraffe/mizanapp/insights/InsightsViewModelTest.kt`
  (new file — later user stories will add more test methods to this same file, not create new
  files). Cover only trend-related behavior at this point: constructing `InsightsViewModel` with a
  fake `GetWeeklyTrend` that returns `TrendOutcome.Ready` → the exposed `StateFlow<InsightsUiState>`
  reaches `status = Status.Ready`, `selectedView = InsightsView.TREND` (the default), and `trend`
  contains one `TrendPointUi` per week with `percentage = (weekSummary.score.fraction * 100).toInt()`
  and `isInProgress = weekSummary.score.elapsedAvailable < weekSummary.score.weekTarget`. Also cover:
  fake returns `TrendOutcome.NoHistory` → `status = Status.RecordNotStarted`; fake returns
  `TrendOutcome.CatalogueUnavailable("x")` → `status = Status.CatalogueUnavailable("x")`;
  `InsightsEvent.Retry` re-invokes the use case and can recover from a failure; initial load sets
  `trendHasMore` from the use case's `hasMore`. Also cover `InsightsEvent.LoadEarlierTrend` (US1
  Acceptance Scenario 3): given `trend` already holds weeks and `trendHasMore = true`, the fake
  `GetWeeklyTrend` is invoked a second time with `before` equal to the `weekKey` of the current
  oldest `TrendPointUi`, and its result is **prepended** to `trend` (not replacing it), updating
  `trendHasMore` from the new response; while that second call is in flight, `isLoadingEarlierTrend =
  true` and the existing `trend` list is untouched (mirrors `HistoryUiState.isLoadingMore`); once
  `trendHasMore = false`, a further `LoadEarlierTrend` event is a no-op (no third call to the fake).
  Run it — it MUST fail (the class does not exist).

- [X] T014 [US1] Create `app/src/main/java/com/giraffe/mizanapp/insights/InsightsViewModel.kt`. A
  Kotlin `ViewModel` (see `app/src/main/java/com/giraffe/mizanapp/history/HistoryViewModel.kt` for
  the exact pattern this project uses: private `MutableStateFlow<InsightsUiState>`, public
  `StateFlow` exposure, a `viewModelScope.launch` load function, an `onEvent(event: InsightsEvent)`
  entry point). Constructor takes `GetWeeklyTrend` only for now (more use cases are added by T024 and
  T038 in later phases — do not add unused constructor parameters ahead of the story that needs
  them). On init, load the trend (`before = null`) and set `status`/`trend`/`trendHasMore` per T013's
  expectations. Handle `InsightsEvent.Retry` by reloading from scratch. Handle
  `InsightsEvent.SelectView` by updating `selectedView` only (no reload needed yet — Trend is the
  only view with data at this point). Handle `InsightsEvent.LoadEarlierTrend`: no-op if
  `trendHasMore` is `false` or a load is already in flight; otherwise set `isLoadingEarlierTrend =
  true`, call `GetWeeklyTrend(before = trend.first().weekKey)`, prepend the returned weeks to `trend`,
  update `trendHasMore`, and set `isLoadingEarlierTrend = false`. **Verify**: run `./gradlew
  :app:test`, T013 now passes.

- [X] T015 [P] [US1] Write `app/src/androidTest/java/com/giraffe/mizanapp/insights/InsightsScreenTest.kt`
  (new file — later phases add more test methods here too). Cover: `Status.Ready` with several
  `TrendPointUi` renders one bar/mark per point with a `testTag` or `contentDescription` exposing its
  percentage (follow the semantics pattern in `app/src/main/java/com/giraffe/mizanapp/history/HistoryScreen.kt`,
  found via T001); a `TrendPointUi` with `isInProgress = true` renders with a visibly different style
  (e.g., an outline instead of a filled bar) from one with `isInProgress = false`; assert **no**
  composable in the tree uses a red/error color (query by color, not by eye — compare against
  `MaterialTheme.colorScheme.error` and fail if any trend element matches it); `Status.RecordNotStarted`
  shows explanatory text and no chart; `Status.CouldNotLoad` shows a retry button that emits
  `InsightsEvent.Retry` when tapped. Also cover US1 Acceptance Scenario 3: a "load earlier" control
  (or equivalent scroll-triggered affordance) emits `InsightsEvent.LoadEarlierTrend` when tapped;
  when `trendHasMore = false`, that control is replaced by (or shows alongside) neutral text
  communicating there is nothing earlier — never an error state; when `isLoadingEarlierTrend = true`,
  the already-rendered bars stay on screen (a small loading indicator only, no blank/flash). Run it —
  it MUST fail (the screen does not exist).

- [X] T016 [US1] Create `app/src/main/java/com/giraffe/mizanapp/insights/InsightsScreen.kt`. A
  `@Composable fun InsightsScreen(state: InsightsUiState, onEvent: (InsightsEvent) -> Unit, modifier:
  Modifier = Modifier)` that dispatches on `state.status` the same way
  `app/src/main/java/com/giraffe/mizanapp/history/HistoryScreen.kt` dispatches on `HistoryUiState.Status`
  (found via T001: `Loading` / `RecordNotStarted` / `CouldNotLoad` / `CatalogueUnavailable` / `Ready`).
  For `Ready`, render a simple view switcher (e.g., a row of three text buttons/segmented control
  reading "Trend" / "Month" / "Sections" that call `onEvent(InsightsEvent.SelectView(...))`), and
  below it, only the Trend chart for now (Month and Sections views are built in later phases — leave
  a `TODO` or an empty `Box` for them, gated on `state.selectedView`). The Trend chart is a plain
  Compose `Row` of bars: for each `TrendPointUi`, a `Box` whose height is proportional to
  `percentage` and whose color comes from `com.giraffe.mizanapp.ui.DayCellColors` (reuse the
  `FULLY_RECORDED`/`PARTLY_RECORDED`-style greens — do not invent a new color). An `isInProgress`
  point gets an outline/border instead of a solid fill. Below or alongside the bars, add a small
  "load earlier" control (button, or a scroll-position-triggered `LaunchedEffect` on a horizontally
  scrollable container) that calls `onEvent(InsightsEvent.LoadEarlierTrend)`; render it only when
  `state.trendHasMore` is `true`, and render neutral boundary text ("You've reached the beginning of
  your record" or similar — no failure framing) when it is `false`; show a small progress indicator
  when `state.isLoadingEarlierTrend` is `true`, without hiding the existing bars. **No red anywhere in
  this file.** In
  `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`, wire the actual `composable` call for
  `Destination.Insights` to render `InsightsScreen` with a `koinViewModel<InsightsViewModel>()`
  (mirror how `Destination.History` wires `HistoryScreen`/`HistoryViewModel`, found via T001).
  **Verify**: run `./gradlew :app:connectedAndroidTest --tests "*InsightsScreenTest*"`, T015 now
  passes.

- [X] T017 [US1] Wire dependency injection in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`
  (no preceding test — Principle I exempts DI wiring). In `domainModule`, add
  `factory { GetWeeklyTrend(get()) }` (Koin resolves the existing `GetHistoryPage` factory
  automatically). In `appModule`, add `viewModel { InsightsViewModel(get()) }`. **Verify**: build the
  app (`./gradlew :app:assembleDebug`) and manually launch it — tapping "Insights" on the Week screen
  must show real data, not a crash from a missing Koin definition.

- [X] T018 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsNoWriteTest.kt`
  (new file — later phases add more test methods here too). Seed a small multi-week history directly
  through the repositories (follow the seeding pattern in
  `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoryNoWriteTest.kt`, found via T001).
  Record the row counts of the day-plan and completion tables. Call `GetWeeklyTrend` several times,
  including at least one call with a non-null `before` (simulating opening Insights and scrolling the
  trend back, US1 AS3). Assert the row counts are **byte-for-byte identical** before and after. This
  is SC-001/FR-009 for the trend view specifically.

**Checkpoint**: User Story 1 is fully functional and independently testable. `./gradlew test
connectedAndroidTest` is green for everything touched so far. This is the suggested MVP stopping
point.

---

## Phase 4: User Story 2 - Monthly overview (Priority: P2)

**Goal**: Opening the Month view of Insights shows every day of a selected calendar month as a
color-banded cell (no-data / untouched / partial / complete / upcoming), matching the same
`DayCellState` the Week Screen already uses, navigable month-to-month within the recorded range.

**Independent Test**: Seed a month with a known mix of complete/partial/untouched days plus some days
before install, open the Month view for that month, verify every cell's state matches its
hand-computed value, including December→January navigation.

### Tests for User Story 2 ⚠️ MUST be written and FAIL before implementation

- [X] T019 [P] [US2] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/insights/BuildMonthOverviewTest.kt`.
  Signature under test (`data-model.md` → "New domain types" → `MonthOverview`, and
  `BuildMonthOverview.kt` per plan.md's file list): a pure function
  `buildMonthOverview(month: YearMonth, today: LocalDate, recordStart: LocalDate?, plans:
  List<DayPlan>, completions: List<Completion>, projectedAvailable: Map<LocalDate, Int>):
  MonthOverview` that calls `buildDayCells(month.atDay(1)..month.atEndOfMonth() as a List<LocalDate>,
  ...)` (T003) internally and wraps the result. Cases: a full month with a mix of the five
  `DayCellState`s renders one `DayCell` per calendar date, in date order; a month straddling
  `recordStart` mid-month shows `OUTSIDE_RECORD` before it and normal states after; the current month
  shows `NOT_YET_ELAPSED` for every date after `today`; `MonthOverview.days.size` equals the number
  of days in that specific month (28/29/30/31 — test at least a 31-day and a 28-day month); **a
  record with exactly one day of history** (SC-005) — `recordStart == today`, one stored plan — still
  returns a full month's `days` list with no crash: that one date renders normally and every other
  date in the month renders `OUTSIDE_RECORD` or `NOT_YET_ELAPSED` as appropriate. Run it — it MUST
  fail (the function does not exist).

- [X] T020 [US2] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/MonthOverview.kt`
  (the `data class MonthOverview(val month: YearMonth, val days: List<DayCell>)` from
  `data-model.md`) and `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/BuildMonthOverview.kt`
  (the function from T019). **Verify**: run `./gradlew :domain:test`, T019 now passes.

- [X] T021 [P] [US2] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetMonthOverviewTest.kt`.
  Signature under test (`contracts/use-cases.md` → `## GetMonthOverview`): `class GetMonthOverview(
  private val plans: DayPlanRepository, private val completions: CompletionRepository, private val
  catalogue: CatalogueRepository, private val time: TimeProvider) { suspend operator fun
  invoke(month: YearMonth): MonthOverviewOutcome }`. Use fake repositories (follow the fake pattern in
  `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetHistoryPageTest.kt`, found via T001
  — reuse the same fakes if they are already generic enough). Cases: a month entirely covered by
  stored plans returns those plans' own figures unchanged; an elapsed date in the month with no
  stored plan is projected using `catalogue.versionEffectiveOn(date)` — **not** `currentVersion()`
  (this is the mid-period catalogue-change guarantee, SC-003); a future date in the month (only
  possible for the current month) is projected using `currentVersion()`; a date before
  `recordStart` gets no projection entry (renders `OUTSIDE_RECORD` via `buildDayCells`); the fake
  `DayPlanRepository`'s `ensurePlanFor` is **never called** (assert this explicitly — it is the
  no-write guarantee, FR-009); a missing catalogue version needed for projection returns
  `MonthOverviewOutcome.CatalogueUnavailable`. Run it — it MUST fail (the class does not exist).

- [X] T022 [US2] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetMonthOverview.kt`
  exactly per T021's signature and `contracts/use-cases.md` → `## GetMonthOverview`. **Verify**: run
  `./gradlew :domain:test`, T021 now passes.

- [X] T023 [P] [US2] Add test methods to the existing
  `app/src/test/java/com/giraffe/mizanapp/insights/InsightsViewModelTest.kt` (from T013 — do not
  create a new file) covering the Month view: `InsightsEvent.SelectView(InsightsView.MONTH)` loads
  the current month via a fake `GetMonthOverview` and sets `state.month` to a `MonthOverviewUi` whose
  `days` is the `DayCell` list mapped to `DayCellUi` (reuse the exact mapping logic already in
  `app/src/main/java/com/giraffe/mizanapp/week/WeekViewModel.kt`, found via T001, where it builds
  `DayCellUi` from a domain `DayCell` — do not invent a second mapping); `InsightsEvent.PreviousMonth`
  and `InsightsEvent.NextMonth` change the loaded month and reload; navigating earlier than the
  record-start month sets `canGoEarlier = false` and further `PreviousMonth` events are no-ops;
  navigating later than the current month sets `canGoLater = false` similarly. Run it — the new test
  methods MUST fail (the ViewModel does not yet handle `MONTH`/`PreviousMonth`/`NextMonth`).

- [X] T024 [US2] Edit `app/src/main/java/com/giraffe/mizanapp/insights/InsightsViewModel.kt`: add a
  `GetMonthOverview` constructor parameter; add internal state tracking the currently viewed
  `YearMonth` (default: the month containing `today`); handle `InsightsEvent.SelectView(MONTH)` by
  loading that month if not already loaded; handle `PreviousMonth`/`NextMonth` by moving the tracked
  month by one and reloading, clamped so navigation cannot go earlier than the record-start month or
  later than the current month (compute `canGoEarlier`/`canGoLater` from those same bounds). **Verify**:
  run `./gradlew :app:test`, all of T023's new methods now pass, and T013's original trend-only tests
  still pass unmodified.

- [X] T025 [P] [US2] Add test methods to the existing
  `app/src/androidTest/java/com/giraffe/mizanapp/insights/InsightsScreenTest.kt` (from T015) covering
  the Month view: selecting "Month" renders a grid with one cell per day of the loaded month;
  `OUTSIDE_RECORD` cells (pre-install) render with a visibly distinct "no data" style from
  `NOTHING_RECORDED` cells (an outline vs. a filled light background, matching
  `com.giraffe.mizanapp.ui.DayCellColors`); tapping the previous/next month controls changes the
  displayed grid; at the record-start month, the previous-month control is disabled; assert no red
  color anywhere in the month grid (same technique as T015). Run it — the new methods MUST fail.

- [X] T026 [US2] Edit `app/src/main/java/com/giraffe/mizanapp/insights/InsightsScreen.kt`: implement
  the Month view (replace the placeholder from T016) as a Compose grid (7 columns, `LazyVerticalGrid`
  or a manual `Column` of `Row`s of 7) of `state.month?.days`, each cell colored via
  `com.giraffe.mizanapp.ui.DayCellColors.containerColorFor(dayCellUi.state)`; add previous/next month
  buttons above the grid, disabled per `state.month?.canGoEarlier` / `canGoLater`, calling
  `onEvent(InsightsEvent.PreviousMonth)` / `onEvent(InsightsEvent.NextMonth)`. **Verify**: run
  `./gradlew :app:connectedAndroidTest --tests "*InsightsScreenTest*"`, T025's new methods now pass.

- [X] T027 [US2] Edit `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` (no preceding test — DI
  wiring is exempt). In `domainModule`, add `factory { GetMonthOverview(get(), get(), get(), get()) }`.
  Update the `InsightsViewModel` registration in `appModule` to pass the additional dependency:
  `viewModel { InsightsViewModel(get(), get()) }` (parameter order must match the constructor from
  T024). **Verify**: `./gradlew :app:assembleDebug` and manually confirm the Month view loads real
  data.

- [X] T028 [P] [US2] Add test methods to the existing
  `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsNoWriteTest.kt` (from T018) covering
  `GetMonthOverview`: navigating across several months writes nothing (same row-count-before/after
  technique as T018). Also create
  `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsCatalogueChangeTest.kt` (new file):
  seed a month's worth of completions under catalogue version 1, capture the month overview's
  per-day states/points, publish catalogue version 2 (different points and/or schedule) effective
  today, re-load the same past month, and assert every captured value is **identical** — this is
  SC-003 for the Month view specifically (the fuller cross-view version of this test is completed in
  T042).

**Checkpoint**: User Stories 1 AND 2 both work independently. `./gradlew test connectedAndroidTest`
is green for everything touched so far.

---

## Phase 5: User Story 3 - Section breakdown and personal bests (Priority: P3)

**Goal**: Opening the Sections view of Insights shows every section's completion rate for the
current week or month, listed in catalogue order with no ranking or "lowest" callout, plus a
personal-bests card showing only the single best day and best week ever recorded.

**Independent Test**: Seed a period where one section is always completed and another rarely is,
verify each section's own rate is shown in catalogue order (not sorted by rate, no badge on the
low one); seed a full record and verify the personal-bests card shows exactly the highest-percentage
day and week, with no "worst" surface anywhere.

### Tests for User Story 3 ⚠️ MUST be written and FAIL before implementation

- [X] T029 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/insights/BuildSectionBreakdownTest.kt`.
  Signature under test (`data-model.md` → `SectionPerformance`, and `BuildSectionBreakdown.kt` per
  plan.md): a pure function `buildSectionBreakdown(plans: List<DayPlan>, completions:
  List<Completion>): List<SectionPerformance>`. Cases: a period where one section is completed every
  occurrence and another rarely — both appear with their own correct `completed`/`available`/`rate`;
  the returned list order matches `sectionOrder` ascending, **not** sorted by `rate` (assert this
  explicitly — it is Clarification Q2); a section relabeled between two plans in the range (different
  `sectionLabel` on an earlier vs. later `PlannedTask` for the same `sectionId`) resolves to the
  label from the most recent date carrying that section; a section with zero applicable occurrences
  anywhere in the range does not appear in the output at all (not a zero row); **a period containing
  exactly one elapsed day** (SC-005 — e.g., a fresh install's first day) still returns correct
  `SectionPerformance` rows computed from that single day's planned tasks, with no crash and no
  divide-by-zero. Run it — it MUST fail (the function does not exist).

- [X] T030 [US3] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/SectionPerformance.kt`
  (the data class from `data-model.md`, including its `rate` computed property) and
  `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/BuildSectionBreakdown.kt` (the
  function from T029). **Verify**: run `./gradlew :domain:test`, T029 now passes.

- [X] T031 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetSectionBreakdownTest.kt`.
  Signature under test (`contracts/use-cases.md` → `## GetSectionBreakdown`): `class
  GetSectionBreakdown(private val plans: DayPlanRepository, private val completions:
  CompletionRepository, private val catalogue: CatalogueRepository, private val time: TimeProvider) {
  suspend operator fun invoke(period: InsightsPeriod): SectionBreakdownOutcome }`. Cases: `period =
  InsightsPeriod.ForWeek(week)` reads only that week's elapsed dates; `period =
  InsightsPeriod.ForMonth(month)` reads only that month's elapsed dates; a date with no stored plan
  uses `deriveDayPlan(catalogue, versionEffectiveOn(date), date)` (the existing `005` function — do
  not write a new derivation) and the fake `DayPlanRepository`'s `ensurePlanFor` is **never called**;
  future dates within the period (e.g., the rest of the current week) are excluded from the read
  entirely, not included with zero completed; missing catalogue version returns
  `SectionBreakdownOutcome.CatalogueUnavailable`. Run it — it MUST fail (the class does not exist).

- [X] T032 [US3] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetSectionBreakdown.kt`
  exactly per T031's signature and `contracts/use-cases.md` → `## GetSectionBreakdown`. **Verify**:
  run `./gradlew :domain:test`, T031 now passes.

- [X] T033 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/insights/BuildPersonalBestsTest.kt`.
  Signature under test (`data-model.md` → `PersonalBests`, and `BuildPersonalBests.kt` per
  plan.md): a pure function `buildPersonalBests(dayCells: List<DayCell>, today: LocalDate):
  PersonalBests` (it receives already-built `DayCell`s — see T036 for how the use case builds them —
  and groups them into weeks internally via `WeekBoundary.weekContaining`). Cases: a single day of
  history → `bestDay` is that day, `bestWeek` is its containing week (if `elapsedAvailable > 0`);
  a record with two days tied at 100% → `bestDay` is the **earlier** of the two; `bestDay` never
  selects a cell in `OUTSIDE_RECORD` or `NOT_YET_ELAPSED` state; `bestWeek` never selects a week
  whose `elapsedAvailable == 0`; an empty `dayCells` list → both fields `null`. Run it — it MUST
  fail (the function does not exist).

- [X] T034 [US3] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/PersonalBests.kt`
  (the data class from `data-model.md`) and
  `domain/src/main/kotlin/com/giraffe/mizanapp/domain/insights/BuildPersonalBests.kt` (the function
  from T033). **Verify**: run `./gradlew :domain:test`, T033 now passes.

- [X] T035 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetPersonalBestsTest.kt`.
  Signature under test (`contracts/use-cases.md` → `## GetPersonalBests`): `class GetPersonalBests(
  private val plans: DayPlanRepository, private val completions: CompletionRepository, private val
  catalogue: CatalogueRepository, private val time: TimeProvider) { suspend operator fun invoke():
  PersonalBestsOutcome }`. Cases: `recordStart == null` → `PersonalBestsOutcome.NoHistory`
  immediately, with no repository range read attempted; a populated record reads exactly
  `plansBetween(recordStart, today)` and `liveBetween(recordStart, today)` once each (assert call
  count, not just result), builds `DayCell`s via `buildDayCells` (T003), and calls
  `buildPersonalBests` (T034) on them; missing catalogue version for an elapsed unplanned date
  returns `PersonalBestsOutcome.CatalogueUnavailable`. Run it — it MUST fail (the class does not
  exist).

- [X] T036 [US3] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetPersonalBests.kt`
  exactly per T035's signature and `contracts/use-cases.md` → `## GetPersonalBests`. **Verify**: run
  `./gradlew :domain:test`, T035 now passes.

- [X] T037 [P] [US3] Add test methods to the existing
  `app/src/test/java/com/giraffe/mizanapp/insights/InsightsViewModelTest.kt` (from T013/T023)
  covering Sections and Personal Bests: `InsightsEvent.SelectView(InsightsView.SECTIONS)` loads the
  section breakdown for the currently-scoped period (default: the current week) via a fake
  `GetSectionBreakdown`, mapping each `SectionPerformance` to a `SectionRowUi` **in the same order
  returned by the use case** (no re-sorting in the ViewModel); `InsightsEvent.SwitchSectionPeriod(
  toMonth = true)` reloads the breakdown scoped to the current month instead, and `toMonth = false`
  switches back to week scope; personal bests load once via a fake `GetPersonalBests` during the
  ViewModel's initial load (not re-triggered by view switching or period switching) and populate
  `state.personalBests` regardless of which view is currently selected. Run it — the new test methods
  MUST fail.

- [X] T038 [US3] Edit `app/src/main/java/com/giraffe/mizanapp/insights/InsightsViewModel.kt`: add
  `GetSectionBreakdown` and `GetPersonalBests` constructor parameters; load personal bests once
  during the same initial load that fetches the trend (T014); add internal state tracking whether
  the section breakdown is scoped to the current week or current month (default: week); handle
  `InsightsEvent.SelectView(SECTIONS)` and `InsightsEvent.SwitchSectionPeriod` by (re)loading the
  section breakdown for the tracked scope and mapping results to `SectionRowUi` preserving use-case
  order. **Verify**: run `./gradlew :app:test`, all of T037's new methods now pass, and every
  earlier `InsightsViewModelTest` method (T013, T023) still passes unmodified.

- [X] T039 [P] [US3] Add test methods to the existing
  `app/src/androidTest/java/com/giraffe/mizanapp/insights/InsightsScreenTest.kt` (from T015/T025)
  covering Sections and Personal Bests: selecting "Sections" renders one row per `SectionRowUi` in
  the order given (assert the on-screen order matches input order, not a re-sort); no row carries any
  badge, icon, or highlight distinguishing it as "lowest" or "worst" (query the tree for any such
  marker and assert none exists); the personal-bests card, when present, shows only a best-day and a
  best-week value and contains no text or element referencing a "worst" or lowest anything; run the
  full no-red-color assertion (T015's technique) across **all three views**, not just Trend — this is
  SC-006. Run it — the new methods MUST fail.

- [X] T040 [US3] Edit `app/src/main/java/com/giraffe/mizanapp/insights/InsightsScreen.kt`: implement
  the Sections view (replace the placeholder) as a plain `Column` of rows, each showing
  `sectionLabel` and `"$percentage%"` in the order `state.sections` is already in — no sorting, no
  icon, no color keyed to the percentage value beyond the same neutral green scale used elsewhere.
  Implement a personal-bests card (visible regardless of `selectedView`, e.g., pinned above or below
  the switcher) rendering `state.personalBests?.bestDay` and `state.personalBests?.bestWeek` only —
  no "worst" case exists in `PersonalBestsUi`, so there is nothing else to render. **Verify**: run
  `./gradlew :app:connectedAndroidTest --tests "*InsightsScreenTest*"`, T039's new methods now pass.

- [X] T041 [US3] Edit `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` (no preceding test — DI
  wiring is exempt). In `domainModule`, add `factory { GetSectionBreakdown(get(), get(), get(),
  get()) }` and `factory { GetPersonalBests(get(), get(), get(), get()) }`. Update the
  `InsightsViewModel` registration in `appModule` to pass both additional dependencies, matching the
  final constructor parameter order from T038. **Verify**: `./gradlew :app:assembleDebug` and
  manually confirm the Sections view and personal-bests card show real data.

- [X] T042 [P] [US3] Add test methods to the existing
  `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsNoWriteTest.kt` covering
  `GetSectionBreakdown` and `GetPersonalBests`: opening the Sections view and switching week/month
  scope writes nothing; computing personal bests writes nothing (same row-count technique as T018).
  Extend `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsCatalogueChangeTest.kt` (from
  T028) to cover the full SC-003 scenario from `quickstart.md`'s "The defining scenario" section:
  seed under catalogue v1, capture trend/month/sections/personal-bests figures for a past period,
  publish catalogue v2 effective today, re-load the same past period, assert every captured figure is
  identical while today's own figures follow v2.

**Checkpoint**: All three user stories are independently functional and work together on one screen.
`./gradlew test connectedAndroidTest` is green for the whole feature.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Performance proof (both Insights' own SC-002 and its non-regression effect on Today per
SC-004), documentation, and the final manual/design audit. Depends on all three user stories being
complete.

- [X] T043 [P] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/InsightsPerformanceTest.kt`.
  Seed a 1-year fixture (~365 plans, following the seeding pattern in
  `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoryPerformanceTest.kt`, found via T001)
  and assert `GetWeeklyTrend`, `GetMonthOverview`, and `GetSectionBreakdown` each complete within 1
  second (SC-002). Separately, seed a 3-year fixture (~1,095 plans, matching `005`'s
  `HistoryPerformanceTest` scale) and assert `GetPersonalBests` completes within 1 second (research.md
  "Full-record scan bound for GetPersonalBests").

- [X] T044 [P] Edit `docs/GLOSSARY.md`: add entries for Aggregation Period, Section Performance,
  Trend, Completion Rate, and Personal Best (the "Domain concepts introduced" list from `spec.md`'s
  Phase 6 section and `data-model.md`'s "New domain types") — add all five entries unconditionally,
  matching the existing glossary's entry format for other terms in that file.

- [X] T045 [P] Write a benchmark for SC-004 ("opening, viewing, or navigating Insights adds no
  observable delay to completing or undoing a task on the Today screen"). Add it to
  `app/src/androidTest/java/com/giraffe/mizanapp/insights/InsightsScreenTest.kt` (or a new
  `app/src/androidTest/java/com/giraffe/mizanapp/today/TodayRecordingPerformanceTest.kt` if a
  Compose-level timing assertion doesn't fit the existing file) that measures the time to record and
  undo a task on `TodayScreen` twice: once as the very first screen opened, and once immediately
  after opening and navigating all three Insights views. Assert the second measurement is not
  meaningfully slower than the first (e.g., within the same order of magnitude / a fixed tolerance
  such as +50ms) — this proves Insights holds no lingering coroutine work, cache, or listener that
  competes with the recording path. Record both measurements in the test's failure message so a
  regression is diagnosable.

- [ ] T046 Run the full manual walkthrough in `specs/006-charts-insights/quickstart.md` under "Manual
  walkthrough", "Failure paths", and "Time paths", in airplane mode on a fresh install (physical
  device or emulator with network disabled). Then run `./gradlew test connectedAndroidTest` and
  confirm everything is green.

- [X] T047 Audit `InsightsScreen` (all three views plus the personal-bests card) against the design
  checklist in `CLAUDE.md` under "Audit any design change against these" (Principle IX: no red, no
  "missed" imagery, no comparative/negative framing anywhere; Principle VI: no add/edit/delete/reorder
  affordance anywhere in the screen). Fix anything found before marking this task complete — this is
  SC-006's final sign-off.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. T001 is a reading task only.
- **Foundational (Phase 2)**: Depends on Phase 1. **Blocks every user story** — none of T011–T042 may
  start before T002–T010 are all checked off, because every story's tests and code import
  `buildDayCells`, `DayCellColors`, `InsightsUiState`, `InsightsEvent`, or `InsightsPeriod`.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2 or US3.
- **User Story 2 (Phase 4)**: Depends on Foundational. Its ViewModel/Screen/DI tasks (T024, T026,
  T027) edit the same three files US1 created (`InsightsViewModel.kt`, `InsightsScreen.kt`,
  `di/Modules.kt`), so **T023–T027 must happen after T011–T018 (US1) are done**, even though the
  underlying domain logic (T019–T022) has no such dependency and could be built in parallel with US1.
- **User Story 3 (Phase 5)**: Same shared-file situation — **T037–T041 must happen after US2's
  ViewModel/Screen/DI edits (T024, T026, T027) are done.** T029–T036 (pure domain functions and use
  cases) have no such dependency.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Domain pure-function test → domain pure function → domain use-case test → domain use case →
  ViewModel test → ViewModel → Screen test → Screen → DI wiring (no test) → data-layer no-write test.
  This order is Principle I applied layer by layer, exactly as `plan.md`'s Constitution Check states.
- A later task never starts before the task(s) whose output it needs are checked off. Do not
  reorder within a story.

### Parallel Opportunities

- T002 (buildDayCells test), T004–T005 (color extraction), T006–T007 (navigation), T008–T010
  (shared types) can all be worked on in parallel within Phase 2 **except** T003 must follow T002,
  and T005 must follow T004.
- Within US1: T011 and T015 can be written in parallel (different files); T013 can be written any
  time after T008/T009 exist, in parallel with T011/T012.
- The **domain-layer** tasks of US2 (T019–T022) and US3 (T029–T036) have no file overlap with each
  other or with US1's domain tasks, and could be built in parallel by a second contributor — but the
  **app-layer** tasks of US2 and US3 (ViewModel/Screen/DI) are strictly sequential after US1's
  app-layer tasks, per the shared-file note above.

---

## Parallel Example: Phase 2 (Foundational)

```bash
Task: "Write BuildDayCellsTest.kt" (T002)
Task: "Create DayCellColors.kt" (T004, does not depend on T002/T003)
Task: "Add Destination.Insights to MainActivity.kt" (T006)
Task: "Create InsightsUiState.kt" (T008)
Task: "Create InsightsEvent.kt" (T009)
Task: "Create InsightsPeriod.kt" (T010)
# T003 (extract buildDayCells) waits for T002. T005 (WeekScreen uses DayCellColors) waits for T004.
# T007 (WeekEvent.OpenInsights) can follow T006 immediately.
```

## Parallel Example: User Story 1 domain layer

```bash
Task: "Write GetWeeklyTrendTest.kt" (T011)
# T012 waits for T011 to exist and fail.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (read the reused code).
2. Complete Phase 2 (Foundational — blocks everything else).
3. Complete Phase 3 (User Story 1 — the weekly trend).
4. **STOP and VALIDATE**: run `./gradlew test connectedAndroidTest`; manually open the app, tap
   "Insights" from the Week screen, confirm the trend renders correctly with seeded data.
5. This is a legitimate, demoable increment on its own (roadmap Phase 6's own P1 story).

### Incremental Delivery

1. Setup + Foundational → shared plumbing ready, nothing user-visible yet beyond a button.
2. Add User Story 1 → weekly trend works → validate → this is the MVP.
3. Add User Story 2 → monthly overview works alongside the trend → validate.
4. Add User Story 3 → sections and personal bests complete the screen → validate.
5. Polish → performance proof, glossary, manual walkthrough, design audit.

Each story adds value without breaking the previous one, but — unlike a fully decoupled multi-screen
feature — US2 and US3's *UI-layer* tasks are sequenced after the prior story's UI-layer tasks because
all three share one `InsightsViewModel`/`InsightsScreen`/DI registration. Their *domain-layer* tasks
(the pure functions and use cases) have no such constraint and are genuinely independent.

---

## Notes

- [P] tasks touch different files and have no incomplete prerequisite among listed tasks.
- [Story] labels (US1/US2/US3) trace every task back to its `spec.md` user story.
- Every implementation task's preceding test task MUST fail for the right reason (missing
  class/function, not a typo) before you write the implementation. If a test doesn't fail first,
  the test is not exercising anything.
- Do not edit an already-passing test file to make a later story's code compile — later stories only
  ever *add* test methods to the existing `InsightsViewModelTest.kt` / `InsightsScreenTest.kt` /
  `InsightsNoWriteTest.kt` files, never remove or weaken an earlier assertion.
- Commit after each task or logical group, per repository convention (see `CLAUDE.md`).
- Run `./gradlew test connectedAndroidTest` at every checkpoint, not just at the end.
