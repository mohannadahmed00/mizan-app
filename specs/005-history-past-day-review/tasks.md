---

description: "Task list for History & Past-Day Review (005)"
---

# Tasks: History & Past-Day Review

**Input**: Design documents from `/specs/005-history-past-day-review/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: MANDATORY. Constitution Principle I is NON-NEGOTIABLE — no production code may be written
before a failing test that requires it. Every test task below must be completed, run, and **observed
to fail for the right reason** before the implementation task that follows it.

---

## READ THIS BEFORE STARTING

You are implementing an increment in an existing, working Android app. Most of what you need already
exists. **Your main risk is building something that already exists, or changing something you were
not asked to change.**

### Rules that override your instincts

1. **Do NOT add any Room DAO method.** Every query this feature needs already exists. If you think
   you need a new one, you have misread the plan — re-read [research.md](./research.md) R3.
2. **Do NOT add any database table, column, or migration.** Schema stays at version 2.
3. **Do NOT add any Gradle dependency.** No navigation library, no paging library.
4. **Do NOT change any repository interface.** `DayPlanRepository`, `CompletionRepository` and
   `CatalogueRepository` are already sufficient.
5. **Do NOT modify these existing test files.** They are the regression net. If they go red, you
   broke something:
   - `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummaryTest.kt`
   - `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummaryBackfillTest.kt`
6. **Do NOT add a way to record, undo, edit, delete, reorder or reprice anything on any past-day or
   history surface.** Constitution Principle VI.
7. **Do NOT use red, crosses, "missed", "failed", "lost", or any negative framing anywhere in this
   feature.** Constitution Principle IX. A day with nothing recorded is a fact, not a failure.
8. **Do NOT read the system clock.** Always inject and use `TimeProvider`. Constitution Principle VII.
9. **Do NOT put Android imports in `:domain`.** It is a `kotlin("jvm")` module — it will not compile.

### Files you should read first (they are the patterns to copy)

| Read this | Because |
|---|---|
| `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummary.kt` | The orchestrating-use-case pattern you will copy twice |
| `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetDaySummary.kt` | How a day is summarised from a plan + completions |
| `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt` | The pure function you will widen in Phase 2 |
| `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/BuildDayPlan.kt` | The function derivation reuses |
| `app/src/main/java/com/giraffe/mizanapp/week/WeekViewModel.kt` | The ViewModel pattern you will copy for history |
| `app/src/main/java/com/giraffe/mizanapp/week/WeekUiState.kt` | The UI-state + sealed-event pattern |
| `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` | The navigation host you will change |

### How to run tests

```bash
./gradlew :domain:test                    # fast, no device
./gradlew :app:test                       # fast, no device
./gradlew :data:connectedAndroidTest      # needs device/emulator
./gradlew :app:connectedAndroidTest       # needs device/emulator
```

### Principle I ordering note

Within every phase below, test tasks precede the implementation they cover. Two clarifications so
you do not get confused:

- **Phase 2 contains the domain-level immutability tests** (T007, T009) because they cover code this
  increment writes, and they must exist before it.
- **Phase 5 (US3) is the instrumented end-to-end integrity suite.** It covers `002`'s already-merged
  persistence plus the use cases built in Phases 3–4. It is a regression suite over shipped
  behaviour, not a test written after new code — that is why it sits later without violating
  Principle I.

---

## Phase 1: Setup

**Purpose**: Confirm you are starting from a green, correct baseline. No code is written here.

- [X] T001 Confirm you are on branch `spec/005-history-past-day-review` by running `git status`; if not, run `git switch -c spec/005-history-past-day-review origin/develop-v1`
- [X] T002 Run `./gradlew :domain:test :app:test` and confirm ALL tests pass before changing anything; if any fail, stop and report — do not start work on a red baseline
- [X] T003 Read `specs/005-history-past-day-review/contracts/use-cases.md` and `contracts/ui-state.md` in full; these contain the exact signatures you must implement

**Checkpoint**: Baseline green, contracts read.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The derivation path and the widened aggregate. **Every user story depends on these.**

**⚠️ CRITICAL**: No user story work may begin until this phase is complete.

### Tests first

- [X] T004 [P] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/history/DeriveDayPlanTest.kt` with a test named `derived plan equals the plan ensurePlanFor would store, field by field`: build a plan with `buildDayPlan(catalogue, version, date, PlanOrigin.BACKFILLED) { "stored-id" }`, call the not-yet-written `deriveDayPlan(catalogue, version, date)`, and assert `date`, `catalogueVersion`, `hijriLabel`, `availablePoints`, `origin` and every `PlannedTask` field except `id` and `dayPlanId` are equal
- [X] T005 [P] In the same file add a test named `derived plan is always marked BACKFILLED` asserting `deriveDayPlan(...).origin == PlanOrigin.BACKFILLED` even when the date is today
- [X] T006 [P] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummaryElapsedTest.kt` with a test named `elapsed date with no stored plan reports projected availability, not zero`: call `buildWeekSummary` for a week where one elapsed in-record date has no plan and `projectedAvailable` holds 69 for it, and assert that day cell's `available == 69` and `state == DayCellState.NOTHING_RECORDED`
- [X] T007 [P] In `BuildWeekSummaryElapsedTest.kt` add a test named `a stored plan always wins over the projection`: give the same date BOTH a stored plan with `availablePoints = 74` AND a `projectedAvailable` entry of 69, and assert `available == 74` — this is the Principle III guard, the stored value must never be overridden
- [X] T008 [P] In `BuildWeekSummaryElapsedTest.kt` add a test named `dates before the record start still report zero available` asserting an `OUTSIDE_RECORD` cell reports `available == 0` even when `projectedAvailable` holds an entry for it
- [X] T009 [P] In `DeriveDayPlanTest.kt` add a test named `changing the catalogue does not change a derived plan for an earlier version`: derive for version 1, then derive again with a catalogue whose version 2 has different points, still passing version 1, and assert both results are equal
- [X] T010 Run `./gradlew :domain:test` and CONFIRM T004–T009 FAIL (compilation failure is an acceptable "fail" here since `deriveDayPlan` does not exist yet). Do not proceed until you have seen them fail.

### Implementation

- [X] T011 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/history/DeriveDayPlan.kt` containing `fun deriveDayPlan(catalogue: Catalogue, version: Int, date: LocalDate): DayPlan = buildDayPlan(catalogue, version, date, PlanOrigin.BACKFILLED) { DERIVED_ID }` plus `private const val DERIVED_ID = "derived"`; add a KDoc stating this plan is NEVER persisted and never returned from a repository
- [X] T012 Modify `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt`: change the `available` calculation so it is `0` for `OUTSIDE_RECORD`, `plan.availablePoints` when a stored plan exists, and `projectedAvailable[date] ?: 0` otherwise. Do NOT change the function signature, `DayCellState`, or the weekly-score calculation
- [X] T013 Update the KDoc on `buildWeekSummary` to state the widened precondition: `projectedAvailable` must hold an entry for every date in the week with no stored plan that is at or after `recordStart`
- [X] T014 Run `./gradlew :domain:test` and confirm T004–T009 now pass AND `BuildWeekSummaryTest` + `GetWeekSummaryBackfillTest` still pass unmodified

**Checkpoint**: Derivation exists and is proven equal to storage. The aggregate handles unplanned elapsed dates. User stories can begin.

---

## Phase 3: User Story 1 - Browse the whole record (Priority: P1) 🎯 MVP

**Goal**: A continuous, scrollable list of Saturday–Friday weeks, newest first, back to the record start.

**Independent Test**: Seed months of records with known gaps and totals, open history, check every visible week row by hand against the seeded dates.

### Tests first

- [X] T015 [P] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetHistoryPageTest.kt` with a test named `first page starts at the week containing today` asserting `page.weeks.first().week.key == WeekBoundary.weekContaining(clock.today()).key`
- [X] T016 [P] [US1] In `GetHistoryPageTest.kt` add `page is continuous with no missing weeks`: seed a record with a twelve-week stretch carrying no completions, request pages until `hasMore` is false, and assert every adjacent pair of weeks starts exactly 7 days apart
- [X] T017 [P] [US1] In `GetHistoryPageTest.kt` add `paging stops at the week containing the record start` asserting the oldest week returned contains `earliestPlanDate()` and `hasMore == false` there
- [X] T018 [P] [US1] In `GetHistoryPageTest.kt` add `no week later than the current week is ever returned` — call with a `before` key for a future week and assert nothing later than today's week comes back
- [X] T019 [P] [US1] In `GetHistoryPageTest.kt` add `an empty record is Ready with no weeks, not an error`: with `earliestPlanDate()` returning null, assert `HistoryOutcome.Ready` with `weeks.isEmpty()` and `hasMore == false`
- [X] T020 [P] [US1] In `GetHistoryPageTest.kt` add `elapsed unplanned dates report the version effective on that date`: seed catalogue v1 effective earlier and v2 effective today, and assert an elapsed unplanned date's `available` matches v1's total for that weekday, not v2's
- [X] T021 [P] [US1] In `GetHistoryPageTest.kt` add `loading a page writes nothing`: use a fake `DayPlanRepository` that throws if `ensurePlanFor` is called, and assert paging through the whole record completes without throwing
- [X] T022 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/history/HistoryViewModelTest.kt` with tests named `first load shows Ready with the newest week first`, `LoadMore appends without clearing the list`, `LoadMore does nothing when hasMore is false`, `empty record shows RecordNotStarted`, `CouldNotLoad exposes a retry that reloads`
- [X] T023 [US1] Run `./gradlew :domain:test :app:test` and CONFIRM T015–T022 FAIL

### Implementation

- [X] T024 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/history/HistoryPage.kt` with `data class HistoryPage(val weeks: List<WeekSummary>, val oldestLoaded: WeekKey?, val hasMore: Boolean, val recordStart: LocalDate?)`
- [X] T025 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetHistoryPage.kt` implementing exactly the signature and the eight guarantees in `contracts/use-cases.md`. Copy the constructor shape from `GetWeekSummary` (`plans`, `completions`, `catalogue`, `time`). Load ONE `plansBetween` and ONE `liveBetween` across the whole page span. Memoise `catalogueAt(version)` results in a local `MutableMap<Int, Catalogue>` so a version is loaded once per call. Call `buildWeekSummary` per week. **Never call `ensurePlanFor`.**
- [X] T026 [US1] Create `app/src/main/java/com/giraffe/mizanapp/history/HistoryUiState.kt` exactly as specified in `contracts/ui-state.md` — `HistoryUiState`, its five `Status` values, `WeekRowUi`, and `sealed interface HistoryEvent { LoadMore; OpenDay(date); Retry }`. Reuse `003`'s `DayCellUi` from `com.giraffe.mizanapp.week` for the seven day positions; do NOT create a new day-cell type
- [X] T027 [US1] Create `app/src/main/java/com/giraffe/mizanapp/history/HistoryViewModel.kt` copying the structure of `WeekViewModel`: private `MutableStateFlow`, public `StateFlow`, `seedIfNeeded()` on load, `onEvent`. Track `oldestLoaded` in a private field. `LoadMore` must set `isLoadingMore = true` and APPEND to `weeks` — never replace the list or reset `status` to `Loading`. Also add a public `refresh()` that reloads the pages already loaded, keeping the same span — `HistoryRoute` calls it on resume (copy `WeekRoute`'s `repeatOnLifecycle(RESUMED)` block), so returning from the recording surface shows what was just recorded (T061b)
- [X] T028 [US1] Register in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`: add `factory { GetHistoryPage(get(), get(), get(), get()) }` beside the existing `GetWeekSummary` line, and `viewModel { HistoryViewModel(get(), get()) }` beside the existing `WeekViewModel` line
- [X] T029 [US1] Run `./gradlew :domain:test :app:test` and confirm T015–T022 pass

### UI (tests before the composable — Principle I exempts only DI wiring and `@Preview`)

- [X] T030 [P] [US1] Create `app/src/androidTest/java/com/giraffe/mizanapp/history/HistoryScreenTest.kt` with tests named `twelve consecutive empty weeks all render`, `record start is stated at the end of the list`, `RecordNotStarted shows a way to start`, `CouldNotLoad shows a retry`, and `no element uses a red colour or a cross glyph`
- [X] T030a [P] [US1] In `HistoryScreenTest.kt` add `the four day-position states render distinctly` (FR-003): build a week row containing a `FULLY_RECORDED` date, an elapsed `NOTHING_RECORDED` date, an `OUTSIDE_RECORD` date and a `NOT_YET_ELAPSED` date, and assert each carries a different content description. `DayCellUi`/`DayCellState` are reused from `003` — this test proves the reuse actually reaches the screen, which nothing else does
- [X] T030b [P] [US1] In `HistoryScreenTest.kt` add `partial catalogue shows the weeks that exist and names what cannot be built` (FR-032): render `Status.CatalogueUnavailable` alongside a non-empty `weeks` list, and assert the existing week rows are still displayed AND the notice is displayed. Assert the list is NOT replaced by the notice
- [X] T031 [US1] Run `./gradlew :app:connectedAndroidTest` and CONFIRM T030 FAILS
- [X] T032 [US1] Create `app/src/main/java/com/giraffe/mizanapp/history/HistoryScreen.kt`: a `LazyColumn` of week rows, each showing the Sat–Fri span, earned/available, and seven day positions using `DayCellState`. Use only the tokens in `CLAUDE.md` (background `#EFECE5`, primary `#0B5D42`, ink `#14211C`, muted `#5C6E66`). Trigger `HistoryEvent.LoadMore` when the last item becomes visible. **No red, no crosses, no "missed"**
- [X] T033 [US1] Add `data object OpenHistory : WeekEvent` to the `WeekEvent` sealed interface in `app/src/main/java/com/giraffe/mizanapp/week/WeekUiState.kt`, and an affordance in `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt` that emits it. Do NOT touch `MainActivity` in this task — T033a does that
- [X] T033a [US1] In `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` make history reachable, using the EXISTING single-`destination` pattern (Phase 6 replaces it with a stack — do not build the stack here). Four edits: (1) add `data object History : Destination` to the sealed interface; (2) in `DestinationSaver`, save it as the string `"HISTORY"` and restore `"HISTORY"` to it; (3) add a `HistoryRoute` composable copying the shape of the existing `WeekRoute` — `koinViewModel<HistoryViewModel>()`, `collectAsStateWithLifecycle()`, pass `state` and `onEvent`; (4) in `AppRoute`, handle `Destination.History` with `BackHandler { destination = Destination.Week }`, and in `WeekRoute`'s event lambda set `destination = Destination.History` when the event is `WeekEvent.OpenHistory`
- [X] T034 [US1] Run `./gradlew :app:connectedAndroidTest` and confirm T030 passes. Then launch the app and confirm you can reach history from the weekly sheet and press back to return

**Checkpoint**: History browsable end to end, back to the record start, writing nothing.

---

## Phase 4: User Story 2 - Read a past day exactly as it was (Priority: P1)

**Goal**: Any recorded day opens showing the tasks that applied on it, their points then, and its totals.

**Independent Test**: Seed one date with a known plan and known completions, open it from history, check every row and the day total by hand.

### Tests first

- [X] T035 [P] [US2] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetDayDetailTest.kt` with `a future date returns NoRecord without reading or writing`
- [X] T036 [P] [US2] In `GetDayDetailTest.kt` add `a date before the record start returns NoRecord without writing`
- [X] T037 [P] [US2] In `GetDayDetailTest.kt` add `a date with a stored plan is summarised without writing` — fake repository throws if `ensurePlanFor` is called
- [X] T038 [P] [US2] In `GetDayDetailTest.kt` add `an eligible unplanned date stores exactly one plan` asserting `ensurePlanFor` was called once and the result is `Ready`
- [X] T039 [P] [US2] In `GetDayDetailTest.kt` add `a failed store still returns Ready with derived figures`: make `ensurePlanFor` throw, and assert `Ready` with `availablePoints` equal to what a derived plan gives, and that no error reaches the caller
- [X] T040 [P] [US2] In `GetDayDetailTest.kt` add `derived and stored summaries are identical`: capture the summary for an unplanned date, let it store, re-read, and assert both `DaySummary` values are equal
- [X] T041 [P] [US2] In `GetDayDetailTest.kt` add `an unresolvable catalogue version returns CatalogueUnavailable, never an empty day` asserting the outcome is `CatalogueUnavailable` and NOT a `Ready` with zero tasks
- [X] T042 [P] [US2] Create `app/src/test/java/com/giraffe/mizanapp/daysummary/DaySummaryViewModelTest.kt` with tests named `Ready exposes sections in plan order`, `NoRecord for a date outside the record`, and `CatalogueUnavailable is a distinct status`
- [X] T042a [P] [US2] In `DaySummaryViewModelTest.kt` add `a backfilled day and an opened day with nothing recorded produce identical state` (FR-014): build two dates with identical plans differing only in `PlanOrigin` (`BACKFILLED` vs `OPENED`), both with no completions, and assert the two resulting `DaySummaryUiState` values are equal. Origin must be invisible — if this fails, something is branching on it
- [X] T043 [US2] Run `./gradlew :domain:test :app:test` and CONFIRM T035–T042 FAIL

### Implementation

- [X] T044 [US2] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetDayDetail.kt` implementing the exact six-step order in `contracts/use-cases.md`. Wrap the `ensurePlanFor` call in `try { ... } catch (e: Exception) { /* best effort, FR-020c */ }`. **After the try/catch, always re-read with `plans.planFor(date)`** rather than using the returned `EnsureOutcome`
- [X] T045 [US2] Add `data class CatalogueUnavailable(val detail: String) : Status` to `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryUiState.kt`. Do NOT add an event type to this file and do NOT add an `isDerived` flag
- [X] T046 [US2] Change `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryViewModel.kt` to take `GetDayDetail` instead of `GetDaySummary` and map the three outcomes to the three statuses. Keep the existing section-grouping code unchanged
- [X] T047 [US2] In `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` add `factory { GetDayDetail(get(), get(), get(), get()) }` and change the `DaySummaryViewModel` line to inject it. Leave the existing `GetDaySummary` registration in place — `GetDayDetail` uses the same summarising logic internally
- [X] T048 [US2] Run `./gradlew :domain:test :app:test` and confirm T035–T042 pass

**Checkpoint**: Any past day opens correctly, from history and from the weekly sheet.

---

## Phase 5: User Story 3 - The record does not change when the catalogue does (Priority: P1)

**Goal**: Prove Principle III. This is the phase `docs/PLAN.md` says exists to justify the whole increment.

**Independent Test**: Seed under catalogue v1, introduce v2 with changed points and schedules, assert every pre-change figure is identical while today follows v2.

> **This suite is the merge gate.** If any assertion here fails, do not proceed — a past figure that
> moved means the app is reading the live catalogue, which is the one bug that cannot be repaired
> after the fact.

- [X] T049 [P] [US3] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/CatalogueChangeHistoryTest.kt` with a helper that seeds catalogue v1, records completions across several weeks including one fully recorded week reading 500/500, and captures every day's task list, per-task points, available points, earned points and containing-week totals
- [X] T050 [US3] In `CatalogueChangeHistoryTest.kt` add `past day figures are unchanged after a point value changes` — introduce v2 effective today changing at least one task's points, and assert every captured pre-change figure is identical
- [X] T051 [US3] In `CatalogueChangeHistoryTest.kt` add `past day figures are unchanged after a schedule rule changes` — v2 gives one task an extra weekday and removes a weekday from another; assert past days list neither change
- [X] T052 [US3] In `CatalogueChangeHistoryTest.kt` add `today follows the new catalogue version` asserting today's available points match v2
- [X] T053 [US3] In `CatalogueChangeHistoryTest.kt` add `a completion keeps the points it was awarded` asserting `pointsAwarded` on pre-change completions is unchanged and still sums into the day, week and history totals
- [X] T054 [US3] In `CatalogueChangeHistoryTest.kt` add `streak figures do not move after a catalogue change` calling `GetStreakSummary` before and after and asserting equality
- [X] T055 [US3] In `CatalogueChangeHistoryTest.kt` add `a plan materialised after the change uses the version effective on that date` — open an elapsed unplanned date under v2 and assert the stored plan's `catalogueVersion` is 1
- [X] T056 [P] [US3] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoryNoWriteTest.kt` with `scrolling the whole record changes nothing stored` — snapshot all `day_plans`, `planned_tasks` and `completions` rows, page through the entire history, snapshot again, assert equality
- [X] T057 [P] [US3] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DayOpenMaterialisationTest.kt` with `opening ten unplanned days creates exactly ten plans and no completions`, `every created plan is marked BACKFILLED`, and `reopening the same day creates nothing`
- [X] T058 [US3] In `DayOpenMaterialisationTest.kt` add `no plan is created for a date before the record start or after today`
- [X] T058a [US3] In `DayOpenMaterialisationTest.kt` add `streak figures are unchanged after browsing and opening days` (SC-011): call `GetStreakSummary` and keep the result, then page through the entire history and open ten unplanned past days, then call it again and assert equality. Browsing creates backfilled plans, and a streak that moved would mean it is reading plans instead of completions
- [X] T058b [P] [US3] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/PastDayNotWritableTest.kt` with `recording against an elapsed date is refused` and `undoing against an elapsed date is refused` (SC-012): call `CompletionRepository.record` and `undoLast` directly with a past date, assert the outcomes are `RecordOutcome.NotWritable` / `UndoOutcome.NotWritable`, and assert the stored completion rows are byte-identical before and after. `002` enforces this through `DayWritePolicy`; this increment re-asserts it because this increment is what makes past days reachable
- [X] T059 [US3] Run `./gradlew :data:connectedAndroidTest` and confirm every test in this phase passes. **If any fails, stop and fix before Phase 6.**

**Checkpoint**: Historical accuracy proven. This is the evidence the PR merge gate requires.

---

## Phase 6: User Story 4 - One rule about what can be written, said plainly (Priority: P2)

**Goal**: The read-only rule is visible, and exactly one surface in the app can record.

**Independent Test**: Open a past day and read every piece of copy; then confirm every route to a writable date arrives at the same surface.

### Tests first

- [X] T060 [P] [US4] Create `app/src/androidTest/java/com/giraffe/mizanapp/daysummary/DaySummaryScreenTest.kt` with tests named `a past day states that recording happens on the current day`, `a task never recorded shows its value and a zero with no fault language`, and `no element uses a red colour or a cross glyph`
- [X] T061 [P] [US4] Create `app/src/test/java/com/giraffe/mizanapp/NavigationRoutingTest.kt` with tests named `today routes to the Today destination from the week sheet`, `today routes to the Today destination from history`, `an elapsed date routes to the DaySummary destination`, and `back from a day opened in history returns to history, not the week sheet`. Test `destinationForDate` and the stack push/pop as plain functions — do not launch an Activity
- [X] T061a [P] [US4] In `NavigationRoutingTest.kt` add `a date that was current when opened stops accepting writes once midnight passes` (FR-015b): with the fake clock, resolve `destinationForDate(date, today)` to `Destination.Today`, advance the clock past local midnight, and assert `DayWritePolicy.isWritable(date)` is now false and re-resolving the same date yields `Destination.DaySummary`
- [X] T061b [P] [US4] In `app/src/test/java/com/giraffe/mizanapp/history/HistoryViewModelTest.kt` add `returning to history after recording shows the updated figures` (SC-013): load a page, record a completion against today through the repository, call the ViewModel's reload path, and assert today's day position and its week's earned points reflect the new completion
- [X] T062 [US4] Run `./gradlew :app:test :app:connectedAndroidTest` and CONFIRM T060–T061 FAIL

### Implementation

- [X] T063 [US4] In `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` write `StackSaver`, a `Saver<List<Destination>, String>` that joins the per-destination encodings `DestinationSaver` already produces (`"TODAY"`, `"WEEK"`, `"HISTORY"`, `"DAY:<iso-date>"`) with `"|"`, and splits on `"|"` to restore. `"|"` cannot appear in an ISO date, so it is safe. `Destination.History` already exists from T033a — do not add it again
- [X] T064 [US4] In `MainActivity.kt` replace the single `destination` field with `var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(listOf<Destination>(Destination.Today)) }`. The visible screen is `stack.last()`. Navigating pushes (`stack = stack + newDestination`). Remove ALL per-screen hard-coded `BackHandler`s, including the one T033a added, and use ONE at the host: `BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }`. With one entry, do not intercept back — the system exits the app
- [X] T065 [US4] In `MainActivity.kt` add one private function `private fun destinationForDate(date: LocalDate, today: LocalDate): Destination = if (date == today) Destination.Today else Destination.DaySummary(date)` and route BOTH `WeekEvent.OpenDay` and `HistoryEvent.OpenDay` through it. Get `today` from the injected `TimeProvider` (`koinInject<TimeProvider>()`), never from `LocalDate.now()`
- [X] T066 [US4] Change `WeekEvent.OpenHistory` handling from T033a's assignment to a push (`stack = stack + Destination.History`), and confirm `HistoryRoute` is unchanged — it was already added in T033a
- [X] T067 [US4] Add the locked-day copy to `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryScreen.kt` — a plain line stating this day is a record and that recording happens on the current day. No warning colour, no reprimand, no disabled-looking controls
- [X] T068 [US4] Run `./gradlew :app:test :app:connectedAndroidTest` and confirm T060–T061 pass

**Checkpoint**: One recording surface, reachable from everywhere, and the rule is stated.

---

## Phase 7: Polish & Cross-Cutting

- [X] T069 [P] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoryPerformanceTest.kt` seeding three years of daily completions and asserting the first history page resolves within 500 ms and a day within 300 ms (SC-015)
- [X] T070 [P] Add `Locked Day` and `Record Start` entries to `docs/GLOSSARY.md`, following the existing entry style (definition, then a contrast paragraph). Do NOT add `Retro-Completion Window` — see research R7. Update the "Eighteen terms" count in the opening line to twenty
- [X] T071 [P] Read every user-visible string added by this feature and confirm none expresses a penalty, warning, or fault (SC-014). List them in the PR description
- [ ] T072 Run the full manual walkthrough in `specs/005-history-past-day-review/quickstart.md` on a fresh install in airplane mode
- [X] T073 Run `./gradlew test connectedAndroidTest` and confirm everything is green, including `003`'s two unmodified week tests
- [ ] T074 Verify with `git log --oneline` that every test commit precedes its implementation commit — this is checked at the PR merge gate (Principle I)

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: needs Phase 1 — **BLOCKS everything else**
- **Phase 3 (US1)**: needs Phase 2. Ends with history fully reachable from the weekly sheet — T033a adds `Destination.History` using the existing single-field navigation, so US1 is a complete, demoable increment on its own
- **Phase 4 (US2)**: needs Phase 2. Independent of Phase 3
- **Phase 5 (US3)**: needs Phases 3 and 4 — the suite exercises both use cases
- **Phase 6 (US4)**: needs Phase 3. It **replaces** the single-field navigation T033a wrote with a back stack; `Destination.History`, `DestinationSaver`'s `"HISTORY"` case and `HistoryRoute` already exist by then and must not be added twice
- **Phase 7 (Polish)**: needs all of the above

### Within each phase

Tests → observe failure → implementation → observe pass. No exceptions (Principle I).

### Parallel opportunities

Tasks marked `[P]` touch different files and can be done in any order within their phase:

- Phase 2: T004–T009 (two test files)
- Phase 3: T015–T022 (two test files), T030, T030a, T030b (same file — parallel with each other only in the sense that they are independent additions; write them together)
- Phase 4: T035–T042, T042a
- Phase 5: T049, T056, T057, T058b (four test files)
- Phase 6: T060, T061, T061a, T061b
- Phase 7: T069, T070, T071

Tasks NOT marked `[P]` either touch a file an earlier task in the same phase touches, or are a
verification step that must observe the state left by the tasks before it.

---

## Implementation Strategy

### MVP scope

**Phases 1 + 2 + 3.** That delivers a browsable, continuous, correct history that writes nothing —
useful on its own, and independently testable against seeded data.

### Incremental delivery

1. Phase 1 + 2 → derivation proven equal to storage
2. + Phase 3 → history browsable (**MVP**, demo here)
3. + Phase 4 → any past day openable
4. + Phase 5 → historical accuracy proven (**merge gate**)
5. + Phase 6 → the rule stated, one recording surface
6. + Phase 7 → performance, glossary, walkthrough

### If you get stuck

- A figure is wrong for an unplanned past date → you are probably passing `currentVersion()` where
  `versionEffectiveOn(date)` belongs. Check `GetHistoryPage` and `GetDayDetail`.
- `BuildWeekSummaryTest` went red → T012 changed more than the `available` calculation. Revert and
  redo, touching only that `when` block.
- A test needs a date "today" → get it from the injected fake clock, never `LocalDate.now()`.
- You think you need a new DAO query → you do not. Re-read research R3.

---

## Notes

- Commit after each task or each test/implementation pair. Test commits MUST precede their
  implementation commits (T074 verifies this).
- `[P]` = different files, no dependency on an incomplete task.
- `[USn]` maps a task to its user story for traceability.
- Total: 82 tasks. Phase 2 is the one that must be right — everything else builds on it.
- Navigation is written twice on purpose: T033a makes history reachable with the existing
  single-destination pattern so User Story 1 ships complete, and T063–T066 replace that with a back
  stack once FR-015 needs one. Do not try to build the stack early — the routing tests that justify
  it live in Phase 6.
