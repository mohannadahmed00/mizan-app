---

description: "Task list for 004-streaks-consistency"
---

# Tasks: Streaks & Consistency

**Input**: Design documents from `/specs/004-streaks-consistency/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: MANDATORY. Constitution Principle I is non-negotiable — no production code may be written
before a failing test that requires it. Every test task below MUST be committed before the
implementation task that follows it. This is checked at the PR merge gate.

**Organization**: Grouped by user story. Phase 2 is shared foundation and blocks every story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on an incomplete task — safe to do in parallel
- **[Story]**: US1–US4, matching spec.md
- Every task names its exact file path

---

## Before you start — read this

`002` and `003` are **already built and merged**. This feature extends working code.

1. **Never create a file that already exists.** Every task says CREATE or MODIFY — obey the verb.
2. **This feature's production code writes nothing.** Nothing under `src/main` may gain a DAO write,
   a repository write, or a call to `ensurePlanFor`, `record` or `undoLast`. If a `src/main` task
   appears to need one, it is being misread — `003` deliberately wrote during a read; this increment
   deliberately does not. **Tests are the exception and must use these freely**: T004 and T030 seed
   history by calling `ensurePlanFor`, `record` and `undoLast`, which is the only way to build a
   record to read. The prohibition is on production paths, never on fixtures.
3. **There is no migration and no schema change.** `MizanDatabase`'s version stays `2`. Do not touch
   `data/schemas/`. If you find yourself editing an entity, stop.
4. **Every completion query filters `reversedAt IS NULL AND deletedAt IS NULL`.** Copy the filter
   from an existing query in `CompletionDao.kt`. Omitting it keeps a date counted after its only
   completion was undone.
5. **No code outside `TimeProvider` may read a clock.** No `LocalDate.now()`, no `Instant.now()`, no
   `System.currentTimeMillis()`, no `ZoneId.systemDefault()`. Not in tests either.
6. **`:domain` is a pure Kotlin JVM module.** Any `import android.*` there will not compile. That is
   intentional. Compose, Room and Koin are equally unavailable there.
7. **Composables are not exempt from test-first.** The constitution exempts only DI module wiring,
   `@Preview` composables, and generated code.
8. **Never use red, a cross, a broken chain, or a negative number** anywhere in this feature — see
   the audit task T057. This is Principle IX and it is the thing most likely to be got wrong.

### Where tests live

| Module | Source set | Runs with |
|---|---|---|
| `:domain` | `domain/src/test/kotlin/…` | `./gradlew :domain:test` (JVM, fast) |
| `:data` | `data/src/androidTest/kotlin/…` | `./gradlew :data:connectedDebugAndroidTest` (needs a device) |
| `:app` ViewModels | `app/src/test/java/…` | `./gradlew :app:testDebugUnitTest` (JVM) |
| `:app` Compose | `app/src/androidTest/java/…` | `./gradlew :app:connectedDebugAndroidTest` (needs a device) |

### Existing test helpers — use these, do not write new ones

| Helper | File | Notes |
|---|---|---|
| `FakeTimeProvider` | `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/FakeTimeProvider.kt` | has `setDate(date, time)`, `advanceBy(duration)`, `setZone(zone)` |
| `FakeClock` | `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt` | the `:app` twin of the above |
| `TestTimeProvider` | `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DbTestBase.kt` | instrumented clock; `setDate(date)` uses 09:00 |
| `DbTestBase` | same file | gives every `:data` test `db`, `time`, `catalogue`, `dayPlans`, `completions` |
| `FakeWeekCompletionRepository` | `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/FakeWeekRepositories.kt` | has a `seed(vararg completions)` seam |
| `FakeCompletionRepository` | `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt` | goes through `record` / `undoLast` like the real one |

### Fixture dates used throughout (verified real weekdays)

| Date | Weekday | Use |
|---|---|---|
| `2026-08-19` | Wednesday | the standard "today" |
| `2026-08-18` | Tuesday | "yesterday" |
| `2026-08-15` … `2026-08-19` | Sat–Wed | a five-day run ending today |
| `2026-08-14` … `2026-08-18` | Fri–Tue | a five-day run ending yesterday |
| `2026-07-01` | Wednesday | the standard record start |
| `2026-08-12` | Wednesday | seven days before today — the break-notice boundary |
| `2026-08-11` | Tuesday | eight days before today — outside the break-notice window |

Zone is `Africa/Cairo` throughout, matching the existing helpers.

### The invariants every domain test asserts

- `current <= longest`, always.
- `todayCounted == true` implies `current >= 1`.
- `isAtRisk` and `todayCounted` are never both true.
- `isAtRisk` and `showBreakNotice` are never both true.
- `recentActivity.size == 7`, unconditionally — including for a completely empty record.

If any of these can be made false, the fold is wrong. Do not relax the invariant.

### Types grow across phases — this is deliberate

`StreakSummary` and `buildStreakSummary` are **built up story by story**, not defined in full up
front:

| Added in | Fields added to `StreakSummary` | Parameters added to `buildStreakSummary` |
|---|---|---|
| US1 (Phase 3) | `current`, `longest`, `lastActiveDate`, `todayCounted` | `consistencyDates`, `today`, `recordStart` |
| US3 (Phase 5) | `recentActivity`, `showBreakNotice` | — |
| US4 (Phase 6) | `isAtRisk` | `now`, `zone` |

This keeps each story independently shippable. **Do not add a field before its phase**, and do not
stub one with a placeholder value — a field that is always `false` because its logic has not been
written yet is exactly the failure mode this ordering avoids.

---

## Phase 1: Setup

**Purpose**: Confirm the starting point. No code changes.

- [X] T001 Confirm the working branch is `spec/004-streaks-consistency` by running `git branch --show-current` at the repository root. If it is not, stop and report — do not create or switch the branch yourself.
- [X] T002 Establish a green baseline by running `./gradlew :domain:test :app:testDebugUnitTest` at the repository root. All tests must pass before any change is made. If any test already fails, stop and report which one — do not start work on a red baseline.

**Checkpoint**: Baseline green, correct branch.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: One build line, and the single read every story depends on — the set of dates carrying
a live completion.

**⚠️ CRITICAL**: No user story work may begin until Phase 2 is complete.

### 2a — Build configuration

- [X] T003 MODIFY `domain/build.gradle.kts`. In the existing `dependencies { }` block, add the line `testImplementation(libs.kotlinx.coroutines.test)` directly beneath `testImplementation(libs.junit)`. Do not add a version — `kotlinx-coroutines-test` is already declared in `gradle/libs.versions.toml` and is already used by `:app`. Do not change any other line in this file, and do not add anything to `implementation`. Verify with `./gradlew :domain:dependencies --configuration testCompileClasspath` that `kotlinx-coroutines-test` now appears.

### 2b — The consistency-dates read (test first)

- [X] T004 [P] CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ConsistencyDatesQueryTest.kt`, package `com.giraffe.mizanapp.data`. Extend `DbTestBase`. Use JUnit 4 (`org.junit.Test`, `org.junit.Assert.assertEquals`) and `kotlinx.coroutines.test.runTest` with `kotlinx.coroutines.flow.first`. Write four tests, each calling `completions.observeConsistencyDates().first()`: (a) **empty record** — after `seedAndPlanToday()` with no completions, the result is an empty list; (b) **one date appears once** — record the same task's allowed occurrences plus several different tasks on `time.today()`, and assert the result is exactly `listOf(time.today())`, size 1; (c) **tombstones excluded** — record exactly one completion for a task, then `completions.undoLast(...)` it, and assert the result is empty; (d) **ascending order across dates** — use `time.setDate(...)` to move the clock forward across three dates, calling `dayPlans.ensurePlanFor(time.today())` and recording one completion on each, then assert the result equals those three dates in ascending order. Run `./gradlew :data:connectedDebugAndroidTest` and confirm it fails to compile — that is the expected first failure.
- [X] T005 MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CompletionRepository.kt`. Add one method to the `CompletionRepository` interface, after `liveBetween`: `fun observeConsistencyDates(): Flow<List<LocalDate>>`. Add a KDoc comment stating: every date carrying at least one live completion, ascending and distinct; a date appears once however many completions it holds; reversed and tombstoned records are excluded; it is deliberately unbounded because the longest streak is unbounded, so no date-range parameter may be added. **Add nothing else** — no write method, no suspend variant.
- [X] T006 [P] MODIFY `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/FakeWeekRepositories.kt`. Add the new `observeConsistencyDates()` override to `FakeWeekCompletionRepository` so `:domain`'s tests still compile. Implement it over the existing `rows` list: return `MutableStateFlow(rows.filter { it.isLive }.map { it.creditedDate }.distinct().sorted())`. Change nothing else in this file.
- [X] T007 [P] MODIFY `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt`. Add the new `observeConsistencyDates()` override to `FakeCompletionRepository` so `:app`'s tests still compile. Implement it over the existing `rows: MutableStateFlow<List<Completion>>` using `map`: `rows.map { all -> all.filter { it.isLive }.map { it.creditedDate }.distinct().sorted() }`. It must be a live flow, not a snapshot — later tasks rely on it re-emitting after `record` and `undoLast`. Change nothing else in this file.
- [X] T008 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CompletionDao.kt`. Add one query method to the `CompletionDao` interface: `@Query("SELECT DISTINCT creditedDate FROM completions WHERE reversedAt IS NULL AND deletedAt IS NULL ORDER BY creditedDate") fun observeLiveDates(): Flow<List<String>>`. Add a comment noting it is covered by the existing `creditedDate` index and that no index is added. **Add no `@Insert`, `@Update` or `@Delete` method.**
- [X] T009 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCompletionRepository.kt`. Implement `observeConsistencyDates()` by mapping `database.completionDao().observeLiveDates()` with `.map { dates -> dates.map(LocalDate::parse) }`. Use the same date conversion the rest of the file already uses for `creditedDate` — if it goes through a helper in `com.giraffe.mizanapp.data.mapper`, use that helper instead of `LocalDate.parse`. Add no other member. If the DAO accessor is named something other than `completionDao()`, use the name already used elsewhere in this file.
- [X] T010 Run `./gradlew :domain:test :app:testDebugUnitTest` and `./gradlew :data:connectedDebugAndroidTest`. All four tests from T004 must now pass and every pre-existing test must still pass. If a pre-existing test broke, fix the break rather than the test.

**Checkpoint**: The record's consistency dates are readable as a live flow. No story work has started.

---

## Phase 3: User Story 1 — See the run (Priority: P1) 🎯 MVP

**Goal**: The current streak and the longest streak appear on Today, computed from the record, with
nothing stored and nothing written.

**Independent test**: Seed several weeks of records with known gaps, open the app, and check the two
figures by hand against the seeded dates.

### 3a — The fold (`:domain`, pure Kotlin, test first)

- [X] T011 [P] [US1] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummaryTest.kt`, package `com.giraffe.mizanapp.domain.streak`. Use JUnit 4. Define `private val today = LocalDate.parse("2026-08-19")` and `private val recordStart = LocalDate.parse("2026-07-01")`, and a helper `private fun dates(vararg iso: String) = iso.map(LocalDate::parse)`. Call `buildStreakSummary(consistencyDates = …, today = today, recordStart = recordStart)` in every case. Assert: (a) run of five ending today (`2026-08-15`…`2026-08-19`) → `current == 5`, `longest == 5`, `todayCounted == true`, `lastActiveDate == today`; (b) run of five ending yesterday (`2026-08-14`…`2026-08-18`), nothing today → `current == 5`, `todayCounted == false`, `lastActiveDate == LocalDate.parse("2026-08-18")`; (c) most recent date is `2026-08-17` (the day before yesterday) → `current == 0`, `todayCounted == false`; (d) empty list → `current == 0`, `longest == 0`, `lastActiveDate == null`; (e) a 12-day run, a one-day gap, then a 3-day run ending today → `current == 3`, `longest == 12`; (f) gaps of two days and of seven days break the run exactly as a one-day gap does; (g) an unbroken run from `recordStart` to `today` → `current` equals the full day count and reaching the record start is **not** treated as a break; (h) a list containing `2026-08-20` and `2026-08-21` (later than today) alongside a run ending today → those dates are ignored entirely and do not appear in `current`, `longest` or `lastActiveDate`. Also assert `current <= longest` in every case. Run `./gradlew :domain:test` and confirm it fails to compile.
- [X] T012 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/StreakSummary.kt`, package `com.giraffe.mizanapp.domain.streak`. Define `data class StreakSummary(val current: Int, val longest: Int, val lastActiveDate: LocalDate?, val todayCounted: Boolean)`. In its `init` block: `require(current >= 0)`, `require(longest >= current)`, and `require(!todayCounted || current >= 1)`. Add a KDoc noting the type is derived on every read and is stored nowhere — there is no repository, DAO or entity for it, and none may be added (FR-012). Import only `java.time.LocalDate`. **Do not add `recentActivity`, `showBreakNotice`, or `isAtRisk`** — those arrive in Phases 5 and 6.
- [X] T013 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt`, package `com.giraffe.mizanapp.domain.streak`. Define `fun buildStreakSummary(consistencyDates: List<LocalDate>, today: LocalDate, recordStart: LocalDate?): StreakSummary`. Implement in this order: (1) filter out any date after `today` and any date before `recordStart` when `recordStart` is non-null, then `distinct().sorted()`; (2) if the result is empty return `StreakSummary(0, 0, null, false)`; (3) walk the sorted list once, tracking the length of the current consecutive run — a date continues the run when it equals the previous date plus one day (`previous.plusDays(1)`), otherwise a new run starts — and keep the maximum run length seen as `longest`; (4) `lastActiveDate` is the final element; (5) `todayCounted` is whether the final element equals `today`; (6) `current` is the length of the final run **only if** its last date is `today` or `today.minusDays(1)`, and `0` otherwise. Add a KDoc stating the catalogue is deliberately not a parameter, so this function cannot consult it (FR-005). The function must be pure: no clock, no I/O, no suspension.
- [X] T014 [US1] Run `./gradlew :domain:test`. Every case in T011 must pass. Do not proceed while any is red.

### 3b — The use case (`:domain`, test first)

- [X] T015 [P] [US1] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummaryTest.kt`, package `com.giraffe.mizanapp.domain.usecase`. Use `kotlinx.coroutines.test.runTest` and `kotlinx.coroutines.flow.first`. Build the subject from `FakeWeekCompletionRepository`, `FakeWeekDayPlanRepository` and `FakeTimeProvider` (call `setDate(LocalDate.parse("2026-08-19"))` on the clock). Assert: (a) with a seeded five-day run ending today, `GetStreakSummary(...)().first().current == 5`; (b) with nothing seeded, the first emission is `current == 0` and does not hang; (c) after collecting the first emission, no day plan has been created for any date — check the fake's stored plans before and after and assert they are identical, which is FR-013. Then add the **clock and timezone cases (FR-015, FR-016, SC-010)**, all against the same seeded five-day run ending `2026-08-19` and all re-collecting `.first()` after each move: (d) **travel forward** — `time.setZone(...)` and `time.setDate(LocalDate.parse("2026-08-21"))` so today moves forward past two dates carrying nothing, then assert `current == 0` — the run ends exactly as it would have if the user had lived through those days; (e) **nothing stored moved** — assert the fake's seeded completions still carry byte-identical `creditedDate` values after (d), because a timezone change re-credits nothing; (f) **travel backward** — set the date to `2026-08-17`, so two seeded dates are now in the future, and assert `current` reads the lower figure (3) with the fake's stored rows again unchanged; (g) **restoring restores exactly** — set the date back to `2026-08-19` and assert the summary equals, field for field, the summary from case (a). No leniency is granted anywhere and no zone is stored — if a case needs the code to detect a change, the case is wrong. Run `./gradlew :domain:test` and confirm it fails to compile.
- [X] T016 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummary.kt`, package `com.giraffe.mizanapp.domain.usecase`. Define:

  ```kotlin
  class GetStreakSummary(
      private val completions: CompletionRepository,
      private val dayPlans: DayPlanRepository,
      private val time: TimeProvider,
  ) {
      operator fun invoke(): Flow<StreakSummary> =
          completions.observeConsistencyDates()
              .map { dates -> buildStreakSummary(dates, time.today(), dayPlans.earliestPlanDate()) }
              .distinctUntilChanged()
  }
  ```

  Add a KDoc stating: it never writes, `ensurePlanFor` is never called from here, and `CatalogueRepository` is deliberately not a dependency so FR-005 is enforced by the constructor. **Do not add a `catch`** — a read that fails must propagate, because a zero produced by a failed read is indistinguishable from a real zero (FR-021b). Handling that is the ViewModel's job in T020.
- [X] T017 [US1] Run `./gradlew :domain:test`. All seven of T015's cases must pass, including the four clock and timezone cases. If (e), (f) or (g) fails, something is re-crediting a stored date — that is FR-015 broken, and it is not fixed by adjusting the test.

### 3c — UI state and ViewModel (`:app`, test first)

- [X] T018 [P] [US1] CREATE the failing test `app/src/test/java/com/giraffe/mizanapp/today/TodayStreakTest.kt`, package `com.giraffe.mizanapp.today`. Use `runTest` and the existing fakes in `FakeRepositories.kt`. Assert: (a) **initial state** — `TodayUiState().streak` is `StreakPanelUi.Resolving`; (b) **resolves to figures** — after the ViewModel loads with one completion recorded today, `state.value.streak` is a `StreakPanelUi.Ready` with `current == 1`; (c) **survives no catalogue** — construct the ViewModel with `FakeCatalogueRepository(failWith = listOf(...))` so the status becomes `Status.CatalogueUnavailable`, and assert `state.value.streak` is still `StreakPanelUi.Ready` and not `Resolving` — this is FR-018b and it is the case most likely to regress; (d) **read failure** — using a completion repository whose `observeConsistencyDates()` throws, assert `state.value.streak` is `StreakPanelUi.Unavailable` and that `state.value.status` is still `Status.Ready`, so the tasks remain usable; (e) **retry** — after a failure, sending `TodayEvent.RetryStreak` with the source no longer throwing produces `StreakPanelUi.Ready`; (f) **updates on undo** — record one completion today (`current == 1`), undo it, and assert `current` returns to `0` without reloading the screen. Run `./gradlew :app:testDebugUnitTest` and confirm it fails to compile.
- [X] T019 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayUiState.kt`. Three changes: (1) add the field `val streak: StreakPanelUi = StreakPanelUi.Resolving` as the **last** parameter of `TodayUiState`, leaving every existing field and computed property untouched; (2) add, in the same file, `sealed interface StreakPanelUi` with `data object Resolving`, `data class Ready(val current: Int, val longest: Int, val todayCounted: Boolean)`, and `data class Unavailable(val detail: String)` — add a comment that `Ready` is a flat snapshot holding no callback and nothing lazy, so Compose tests can drive every case without a database; (3) add `data object RetryStreak : TodayEvent` to the existing `TodayEvent` sealed interface, with a comment that it re-subscribes to a read and can author nothing (Principle VI). **Do not add `recentActivity`, `showBreakNotice` or `isAtRisk` to `Ready`** — Phases 5 and 6 add those.
- [X] T020 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt`. Five changes, in this order: (1) add `private val getStreakSummary: GetStreakSummary` as the last constructor parameter; (2) add `private var streakJob: Job? = null` and a `private fun observeStreak()` that cancels any existing `streakJob` and launches a new one in `viewModelScope` collecting `getStreakSummary()`, mapping each `StreakSummary` to `StreakPanelUi.Ready(...)` and assigning it with `_state.value = _state.value.copy(streak = …)` — wrap the flow with `.catch { e -> _state.value = _state.value.copy(streak = StreakPanelUi.Unavailable(e.message ?: "could not read the record")) }` before collecting; (3) call `observeStreak()` from `init`, **on its own line before or after `load()` but not inside it**, so the streak subscribes independently and the day's tasks never wait for it (FR-018c); (4) **preserve the panel at all three places that build a fresh state** — the seed-failure branch in `load()`, the `NoCatalogue` branch in `openDate()`, and the whole-state construction at the end of `emit()` — by adding `streak = _state.value.streak` to each `TodayUiState(...)` call; (5) handle `TodayEvent.RetryStreak` in `onEvent` by setting the panel back to `StreakPanelUi.Resolving` and calling `observeStreak()` again. Change nothing else — the day's tasks, scoring, section position and rollover behaviour must be untouched.
- [X] T021 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`. Two lines: add `factory { GetStreakSummary(get(), get(), get()) }` to `domainModule` beside the existing `GetWeekSummary` and `GetDaySummary` factories, and change `viewModel { TodayViewModel(get(), get(), get(), get()) }` in `appModule` to pass a fifth `get()`. Add the matching import. This file is the one place claiming Principle I's exemption — it is configuration, not behaviour, so it needs no test of its own.
- [X] T022 [US1] Run `./gradlew :app:testDebugUnitTest`. All six cases in T018 must pass, and every pre-existing `TodayViewModelTest` case must still pass. The pre-existing cases are the regression net for the restructure in T020 — if one of them broke, the restructure went too far.

### 3d — The element on screen (`:app`, test first)

- [X] T023 [P] [US1] CREATE the failing test `app/src/androidTest/java/com/giraffe/mizanapp/today/StreakElementTest.kt`, package `com.giraffe.mizanapp.today`. Use `createComposeRule()` and drive `StreakElement(panel = …, onRetry = {})` directly — no database, no ViewModel. Assert: (a) `Ready(current = 5, longest = 12, todayCounted = true)` displays both figures; (b) `Ready(current = 0, longest = 0, todayCounted = false)` displays an unstarted-record message and **not** a bare "0" presented as a result — assert on the message's text; (c) `Resolving` displays no digit at all — assert `onNodeWithText("0").assertDoesNotExist()`, which is the specific failure mode FR-018c names; (d) `Unavailable("…")` displays a message and a retry control, and clicking the retry invokes the `onRetry` lambda; (e) **today pending is distinct from the count (FR-019)** — `Ready(current = 5, longest = 12, todayCounted = false)` shows the same figure 5 **and** a pending marker that is absent from case (a). Assert the marker exists here and does not exist in (a), using `onNodeWithTag("streak-today-pending")`; a test that only checks the number cannot tell the two states apart, which is the whole point of FR-019. Add `Modifier.testTag("streak-element")` expectations so the element can be found as a whole. Run `./gradlew :app:connectedDebugAndroidTest` and confirm it fails to compile.
- [X] T023a [P] [US1] CREATE the failing test `app/src/androidTest/java/com/giraffe/mizanapp/today/TodayScreenStreakTest.kt`, package `com.giraffe.mizanapp.today`. This is SC-016, and it is the only test that exercises the element's **placement** rather than its content. Render `TodayScreen(state = …, onEvent = …, onOpenWeek = {})` with a state holding several sections and `streak = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = true)`. Assert: (a) the node tagged `streak-element` exists on the first block; (b) after firing `TodayEvent.NextSection` to the last block and back again with `TodayEvent.PreviousSection` — driving the state through the ViewModel or by re-rendering with an updated `currentSectionIndex`, whichever the test can do without a database — the element still exists and still displays 5 and 12 unchanged (FR-018a); (c) with `status = Status.CatalogueUnavailable("…")` and the same `Ready` panel, the element is still displayed (FR-018b); (d) with `status = Status.Loading`, the element is **not** displayed. Run `./gradlew :app:connectedDebugAndroidTest` and confirm it fails.
- [X] T024 [US1] CREATE `app/src/main/java/com/giraffe/mizanapp/today/StreakElement.kt`, package `com.giraffe.mizanapp.today`. Define `@Composable fun StreakElement(panel: StreakPanelUi, onRetry: () -> Unit, modifier: Modifier = Modifier)`. Render a `when` over the three `StreakPanelUi` cases per the table in [contracts/ui-state.md](./contracts/ui-state.md). Apply `Modifier.testTag("streak-element")` to the root and `Modifier.testTag("streak-today-pending")` to the marker shown when `todayCounted` is false. Match the visual conventions already in `TodayScreen.kt` — `MaterialTheme.typography`, `Card`, `dp` spacing — and use no colour literal of your own. **Forbidden anywhere in this file**: red, `✗`, "missed", "failed", "lost", "broken", a negative number, and any exclamation of alarm. `Resolving` renders the element's space with a label and no figure. `Unavailable` renders a message blaming the app (for example "Couldn't read your record just now") and a `TextButton` calling `onRetry` — never a `0`, and never nothing at all.
- [X] T025 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayScreen.kt`. Render `StreakElement(state.streak, onRetry = { onEvent(TodayEvent.RetryStreak) })` in **two** places: at the top of `ReadyState`'s content, above the existing header, and inside `CatalogueUnavailableState` so the figures survive a missing catalogue (FR-018b) — this second one is the easy one to forget and requires passing `state` and `onEvent` into that composable, which currently takes only `detail`. **Do not** render it in `LoadingState`. Because Today is a stepped flow, the element must sit outside whatever renders the current prayer block, so stepping between blocks cannot move or change it (FR-018a).
- [X] T026 [US1] Run `./gradlew :app:connectedDebugAndroidTest`. T023's five cases and T023a's four must pass, and every pre-existing screen test must still pass. If T023a case (c) fails, T025's second render site was missed — that is the one this whole task exists to catch.

**Checkpoint**: The run and the longest run are on screen, correct, and survive both a missing
catalogue and a failed read. This alone is a shippable increment.

---

## Phase 4: User Story 2 — A day counts once, for the right reason (Priority: P1)

**Goal**: Prove the criterion — a live completion, once per date, never the catalogue and never a
plan's existence. Almost entirely tests; the rule itself is enforced by the shapes built in Phase 2
and Phase 3.

**Independent test**: Seed one date with one completion, then with forty, then with one that is
undone, and confirm the day counts once, once, and not at all.

- [X] T027 [P] [US2] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/ConsistencyDayTest.kt`, package `com.giraffe.mizanapp.domain.streak`. Assert `isConsistencyDay(date, consistencyDates)` is `true` when the set contains the date and `false` when it does not, including for an empty set. Run `./gradlew :domain:test` and confirm it fails to compile.
- [X] T028 [US2] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/ConsistencyDay.kt`, package `com.giraffe.mizanapp.domain.streak`. Define `fun isConsistencyDay(date: LocalDate, consistencyDates: Set<LocalDate>): Boolean = date in consistencyDates`. The KDoc carries the weight here and must state: a date counts when at least one live completion is credited to it (FR-001); it is a yes or no, so a date with forty completions counts exactly as much as a date with one (FR-002); the set arrives already filtered of reversed records, so nothing downstream re-checks liveness; and `PlanOrigin` and a plan's existence are deliberately **not** consulted (FR-004), because `DayWritePolicy` admits completions only on the current date, which makes a completion sufficient evidence the app was open. Add a final note that **Phase 5 must revisit this** — retroactive completion breaks that premise. Then, in the same task, MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt` so `todayCounted` is computed as `isConsistencyDay(today, retained.toSet())` instead of an inline membership test. **This is not optional**: a predicate that exists only to carry a KDoc is an abstraction with no user, which Principle VIII rules out. It is a refactor of a line T011 already covers, so no new test is needed — T011 and T027 must both stay green.
- [X] T029 [P] [US2] MODIFY `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummaryTest.kt` (from T011). Add two cases: (a) **a duplicated date changes nothing** — pass a list containing the same date three times and assert the summary is identical to the same list with the date once, proving the fold cannot weight a day by how much was done; (b) **an elapsed date with no entry breaks the run** — a run whose second-to-last date is missing yields `current` equal to the trailing segment only.
- [X] T030 [P] [US2] CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/StreakBackfillTest.kt`, package `com.giraffe.mizanapp.data`. Extend `DbTestBase`. Advance the clock across a week without recording anything so `003`'s backfill creates `BACKFILLED` plans for the elapsed dates (call `dayPlans.ensurePlanFor(date)` for each elapsed date exactly as `GetWeekSummary` does), then assert `completions.observeConsistencyDates().first()` is empty — a plan's existence contributes nothing (FR-004, SC-005). Then record one completion on the current date and assert the result contains exactly that one date.
- [X] T031 [P] [US2] CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/StreakImmutabilityTest.kt`, package `com.giraffe.mizanapp.data`. This discharges Principle III for this increment — see [research R7](./research.md#r7--how-is-principle-iii-satisfied-by-an-increment-that-changes-no-schema). Seed a catalogue, create plans and completions across several dates, and compute `buildStreakSummary` from `completions.observeConsistencyDates().first()`. Then load a second catalogue version with **different point values and a different schedule rule** (follow the pattern in the existing `HistoricalImmutabilityTest.kt` in the same directory), and recompute. Assert every field of the summary is identical before and after. A figure that moves means the streak is reading the catalogue, which FR-005 forbids.
- [X] T032 [US2] Run `./gradlew :domain:test` and `./gradlew :data:connectedDebugAndroidTest`. All of T027–T031 must pass.

**Checkpoint**: The criterion is proven end to end, including the two ways it could be wrong that
would still produce plausible-looking numbers.

---

## Phase 5: User Story 3 — Recent activity and the ended run (Priority: P2)

**Goal**: Seven positions showing which recent dates counted, and a break notice that appears while
the break is recent and then stops.

**Independent test**: Seed a recent stretch mixing recorded and unrecorded days, one of them before
the record start, and check every position; then seed a broken run and review the copy.

### 5a — The seven-day window (`:domain`, test first)

- [X] T033 [P] [US3] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/RecentActivityTest.kt`, package `com.giraffe.mizanapp.domain.streak`. With `today = 2026-08-19` and `recordStart = 2026-08-17`, call `buildRecentActivity(consistencyDates, today, recordStart)` and assert: (a) the result always has exactly 7 entries, oldest first, with `today` last — including when `consistencyDates` is empty and when `recordStart` is `null`; (b) a date in the set reads `COUNTED`; (c) an elapsed date not in the set, on or after `recordStart`, reads `NOT_RECORDED`; (d) `today`, when not in the set, reads `TODAY_PENDING` and **not** `NOT_RECORDED`; (e) every date before `recordStart` reads `OUTSIDE_RECORD` and **not** `NOT_RECORDED`; (f) with `recordStart == null` (no records at all), all seven read `OUTSIDE_RECORD` except `today`, which reads `TODAY_PENDING`. Run `./gradlew :domain:test` and confirm it fails to compile.
- [X] T034 [US3] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/RecentActivity.kt`, package `com.giraffe.mizanapp.domain.streak`. Define `enum class ActivityState { COUNTED, NOT_RECORDED, TODAY_PENDING, OUTSIDE_RECORD }`, `data class ActivityDay(val date: LocalDate, val state: ActivityState)`, and `fun buildRecentActivity(consistencyDates: Set<LocalDate>, today: LocalDate, recordStart: LocalDate?): List<ActivityDay>` returning the seven dates `today.minusDays(6)`…`today` in ascending order. Precedence for each date, in this order: in the set → `COUNTED`; equal to `today` → `TODAY_PENDING`; `recordStart == null` or the date is before `recordStart` → `OUTSIDE_RECORD`; otherwise → `NOT_RECORDED`. Add a KDoc explaining why there are four states and not two: collapsing `TODAY_PENDING` shows a day the user has not had yet as a day they missed, and collapsing `OUTSIDE_RECORD` opens a fresh install on six failures. Both are Principle IX violations that a two-state model makes unavoidable, which is why the states live here and not in the composable. State that none of the four is a failure state and none may acquire a colour or glyph that reads as one.
- [X] T035 [P] [US3] MODIFY `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummaryTest.kt`. Add the break-notice cases, all with `today = 2026-08-19`: (a) `lastActiveDate == 2026-08-12` (seven days before today) and `current == 0` → `showBreakNotice == true`; (b) `lastActiveDate == 2026-08-11` (eight days before) → `showBreakNotice == false`; (c) a live run (`current >= 1`) → `showBreakNotice == false` regardless of dates; (d) an empty record (`longest == 0`) → `showBreakNotice == false`, because there is no run to have ended. Also assert `recentActivity.size == 7` in every existing case. This task precedes both T036 and T037 — the test comes before the field and before the logic.
- [X] T036 [US3] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/StreakSummary.kt`. Add two fields to `StreakSummary`: `val recentActivity: List<ActivityDay>` and `val showBreakNotice: Boolean`. Add to the `init` block: `require(recentActivity.size == 7)` and `require(!showBreakNotice || current == 0)`. Commit this together with T037 — a type carrying a field nothing populates is not a shippable state.
- [X] T037 [US3] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt`. Populate the two new fields: `recentActivity = buildRecentActivity(retainedDates.toSet(), today, recordStart)`, and `showBreakNotice = current == 0 && longest > 0 && lastActiveDate != null && !lastActiveDate.isBefore(today.minusDays(7))`. Note in a comment that the window is derived from `lastActiveDate` and nothing records that the notice has been shown — a stored flag would be the only writable state in the feature (FR-021a). The empty-record early return must now also produce seven `recentActivity` entries.
- [X] T038 [US3] Run `./gradlew :domain:test`. T033, T035 and every earlier domain test must pass.

### 5b — On screen (`:app`, test first)

- [X] T039 [P] [US3] MODIFY `app/src/test/java/com/giraffe/mizanapp/today/TodayStreakTest.kt`. Add one case: after recording a completion today, `state.value.streak` is a `Ready` whose `recentActivity` has 7 entries with the last one `COUNTED`.
- [X] T040 [P] [US3] MODIFY `app/src/androidTest/java/com/giraffe/mizanapp/today/StreakElementTest.kt`. Add: (a) a `Ready` with a mixed seven-day window renders seven positions; (b) a `Ready` with `showBreakNotice = true`, `current = 0`, `longest = 38` renders the longest figure and a message presenting it as standing, with no word from the forbidden list in T024; (c) a `Ready` with `showBreakNotice = false`, `current = 0`, `longest = 38` renders no reference to an ended run.
- [X] T041 [US3] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayUiState.kt`. Add `val recentActivity: List<ActivityDayUi>` and `val showBreakNotice: Boolean` to `StreakPanelUi.Ready`, and define `data class ActivityDayUi(val date: LocalDate, val state: ActivityState)` in the same file, importing `ActivityState` from `com.giraffe.mizanapp.domain.streak`.
- [X] T042 [US3] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt`. Map the two new `StreakSummary` fields into `StreakPanelUi.Ready` in `observeStreak()`. No other change.
- [X] T043 [US3] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/StreakElement.kt`. Render the seven positions as four visually distinct neutral treatments — for example a filled mark, an outlined mark, a dotted mark for today-pending, and a faint mark for outside-the-record. **None may be red, a cross, or an empty slot that reads as a hole.** Render the break notice when `showBreakNotice` is true, leading with the longest run as standing and naming the next step — the design's own wording is "Your 38-day record still stands… One task today puts you back on". Add no dismiss control: the notice disappears on its own after seven days (FR-021a).
- [X] T044 [US3] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`.

**Checkpoint**: Recent activity is legible and an ended run is reported without fault.

---

## Phase 6: User Story 4 — A nudge before midnight (Priority: P3)

**Goal**: From 20:00 until local midnight, a user with a live run and nothing recorded today sees
that today is still open — and the state changes on its own as the clock crosses those boundaries.

**Independent test**: With a fake clock, advance to 19:59 then 20:00 on a day with a live run and
nothing recorded, and confirm the state appears exactly once and at the right moment.

### 6a — The time rule (`:domain`, test first)

- [X] T045 [P] [US4] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/StreakClockTest.kt`, package `com.giraffe.mizanapp.domain.streak`. Build instants with `LocalDate.parse("2026-08-19").atTime(h, m).atZone(ZoneId.of("Africa/Cairo")).toInstant()`. Assert: (a) `StreakClock.isAtRiskWindow(19:59, zone)` is `false`; (b) at `20:00` it is `true` — the boundary is inclusive; (c) at `23:59` it is `true`; (d) at `00:00` and `09:00` it is `false`; (e) `StreakClock.nextBoundaryAfter(09:00, zone)` is the same date at `20:00`; (f) `nextBoundaryAfter(20:00, zone)` is the **next** date at `00:00`; (g) `nextBoundaryAfter(23:59, zone)` is the next date at `00:00`; (h) every returned instant is strictly after the one passed in — assert this for all of the above, because a non-strict result would make the scheduler in T050 spin. Run `./gradlew :domain:test` and confirm it fails to compile.
- [X] T046 [US4] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/StreakClock.kt`, package `com.giraffe.mizanapp.domain.streak`. Define `object StreakClock` with `val AT_RISK_FROM: LocalTime = LocalTime.of(20, 0)`, `fun isAtRiskWindow(now: Instant, zone: ZoneId): Boolean` returning whether the local time of day is at or after `AT_RISK_FROM`, and `fun nextBoundaryAfter(now: Instant, zone: ZoneId): Instant` returning the next of today's 20:00 and tomorrow's midnight, whichever comes first and is strictly after `now`. It reads no clock itself — the instant and zone are always passed in, exactly as `DayBoundary.dateAt` takes them. Add a KDoc stating that this is the single home of the 20:00 rule (Principle VII) and that the threshold is a constant rather than a setting, because settings are outside the MVP.
- [X] T047 [P] [US4] MODIFY `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummaryTest.kt`. Add the at-risk cases, each passing a `now` instant built as in T045: (a) `current >= 1`, not counted today, local time 20:00 → `isAtRisk == true`; (b) the same at 19:59 → `false`; (c) `current == 0` at 21:00 → `false`; (d) `todayCounted == true` at 21:00 → `false`. This is the failing test for T048a and T048b and must be committed before either.
- [X] T048a [US4] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/StreakSummary.kt`. Add `val isAtRisk: Boolean`. Add to `init`: `require(!isAtRisk || !todayCounted)` and `require(!isAtRisk || current >= 1)` — together these make "at risk" and "today counted" mutually exclusive, and "at risk" and "showBreakNotice" mutually exclusive by construction.
- [X] T048b [US4] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt`. Add the parameters `now: Instant` and `zone: ZoneId` to `buildStreakSummary` (after `today`, before `recordStart`) and set `isAtRisk = current >= 1 && !todayCounted && StreakClock.isAtRiskWindow(now, zone)`. Update every existing call site in `BuildStreakSummaryTest.kt` and in `GetStreakSummary.kt`. Note in a comment why `now` and `zone` are parameters rather than a pre-computed flag: passing a flag would put the 20:00 rule in the caller and give Principle VII two homes. Commit T048a and T048b together — the field and the logic that fills it are one change.
- [X] T049 [P] [US4] MODIFY `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummaryTest.kt`. **This is the failing test for T050 and must be committed before it.** Add virtual-time cases using `runTest` and `kotlinx.coroutines.test.advanceTimeBy` with `kotlinx.coroutines.flow.toList` over a `take(n)`: (a) with the fake clock at 19:00 on a day with a live run and nothing recorded, the first emission has `isAtRisk == false`; advance the fake clock to 20:00 **and** advance virtual time past the scheduled wait, and assert a second emission arrives with `isAtRisk == true` — with no user action; (b) with the fake clock at 23:30 on a day whose run is live and counted, advance past midnight the same way and assert a new emission reflects the new date; assert additionally that **no emission in the collected sequence reads a `current` lower than both the emission before it and the emission after it** (SC-011) — a momentary dip at rollover is the failure this case exists to catch. Remember to move `FakeTimeProvider` **and** virtual time — advancing only one will hang or produce an identical emission that `distinctUntilChanged` swallows. Run `./gradlew :domain:test` and confirm it fails.
- [X] T050 [US4] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummary.kt` to re-emit at each boundary. Replace the body with:

  ```kotlin
  operator fun invoke(): Flow<StreakSummary> =
      combine(
          completions.observeConsistencyDates(),
          boundaryTicks(),
      ) { dates, _ -> dates }
          .map { dates ->
              buildStreakSummary(dates, time.today(), time.now(), time.zone(), dayPlans.earliestPlanDate())
          }
          .distinctUntilChanged()

  private fun boundaryTicks(): Flow<Unit> = flow {
      emit(Unit)
      while (true) {
          val now = time.now()
          val wait = Duration.between(now, StreakClock.nextBoundaryAfter(now, time.zone()))
          delay(wait.toMillis().coerceAtLeast(1L))
          emit(Unit)
      }
  }
  ```

  The `emit(Unit)` before the loop is what makes the first summary appear immediately; without it `combine` would wait for the first boundary. The `coerceAtLeast(1L)` is a guard against a zero wait — it must never be reachable if `nextBoundaryAfter` is strict, and it is there so a mistake degrades into a slow loop rather than a hang. The four earlier cases in `GetStreakSummaryTest` from T015 must keep passing unchanged.
- [X] T051 [US4] Run `./gradlew :domain:test`. Every case in T045, T047 and T049 must pass, and no test may take more than a second — if one hangs, `nextBoundaryAfter` is returning a non-strict instant.

### 6b — On screen (`:app`, test first)

- [X] T052 [P] [US4] MODIFY `app/src/androidTest/java/com/giraffe/mizanapp/today/StreakElementTest.kt`. Add: a `Ready` with `isAtRisk = true` renders a message naming what is still possible, and contains no countdown, no warning colour, and none of the forbidden words from T024; a `Ready` with `isAtRisk = false` renders no such message.
- [X] T053 [US4] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/TodayUiState.kt` to add `val isAtRisk: Boolean` to `StreakPanelUi.Ready`, and `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt` to map it from `StreakSummary`.
- [X] T054 [US4] MODIFY `app/src/main/java/com/giraffe/mizanapp/today/StreakElement.kt` to render the at-risk message when `isAtRisk` is true. Copy must state what is still possible — for example "Today is still open. One task keeps your 12-day run going." Add no dismiss or snooze control: it clears on the first completion or at midnight (FR-026).
- [X] T055 [US4] Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:connectedDebugAndroidTest`.

**Checkpoint**: All four stories complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T056 [P] MODIFY `docs/GLOSSARY.md`. Add two entries in the existing house style — a `## Longest Streak` section directly after `## Streak`, and a `## Streak Break` section after it. **Longest Streak**: the longest run of consecutive Consistency Days anywhere in the record; it never decreases because a run ended, only if the records behind it are reversed; on screen it may be called the best streak, but Longest Streak is the name it has everywhere else. **Streak Break**: the point at which a run ends — an elapsed date, earlier than today, that is not a Consistency Day; a boundary read out of the record, never a stored event. Update the opening line's term count from "Sixteen terms" to "Eighteen terms". Both terms are named by `docs/PLAN.md` as concepts this phase introduces, so this is a deliverable, not tidying.
- [X] T057 [P] Audit every string, colour and state this feature can produce, against the design list in `CLAUDE.md` (SC-013). Read `StreakElement.kt` end to end and confirm: no red or amber colour value; no `✗`, `×` or broken-chain glyph; no empty slot that reads as a hole rather than as a day; no negative number; no countdown framed as a penalty; and nothing attributing fault — including the `Unavailable` copy, which must blame the app. Check all four `ActivityState` values and all three `StreakPanelUi` cases, including the ended-run, never-started and read-failure copy. Also confirm **FR-024**: the element introduces no Arabic content of its own, and its placement on `TodayScreen` does not sit inside or between the Arabic task rows in a way that could reflow them — `002`'s existing bidirectional handling must be left doing its job untouched. Record the result in the PR description.
- [X] T058 [P] Verify nothing was written and no clock was read. **Scope: `src/main` only.** Test sources legitimately call these to seed history (T004, T030) and are excluded — run the searches against production sources alone, for example `git diff --name-only origin/develop-v1... -- '*/src/main/*'` piped into the greps. (a) **No writes (SC-009)**: none of `ensurePlanFor`, `record(`, `undoLast`, `@Insert`, `@Update`, `@Delete` or `.execSQL` may appear in any `src/main` file this feature added or changed. (b) **No clock reads (FR-014, Principle VII)**: none of `LocalDate.now()`, `Instant.now()`, `System.currentTimeMillis()` or `ZoneId.systemDefault()` may appear in any file this feature touched, **including tests** — that one has no exemption, because a test that reads the real clock is not testing rollover, it is waiting for it. (c) **No schema movement**: `MizanDatabase`'s `version` is still `2` and `git status data/schemas/` shows no change. If (a) or (c) fails, the feature has grown a write it must not have.
- [X] T059 Measure performance (SC-014) with a temporary instrumented test in `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/`: seed roughly three years of daily completions (~1,095 dates), then time from subscribing to `observeConsistencyDates()` through `buildStreakSummary` to the first summary. It must complete within 100 ms on a mid-range device. Then time `record` and `undoLast` with the streak flow collected and without it, and confirm they do not differ — `docs/PLAN.md`'s definition of done for this phase is "requires no new writes on the completion path", and this is the measurement of it. Record both figures in the PR description. **Keep the test, annotated `@org.junit.Ignore("performance probe — run manually, see KDoc for measured figures")`**, with the two measured numbers written into its KDoc. Do not delete it and do not leave it running in CI: a timing assertion on shared emulator hardware is flaky, and a deleted probe means the next person re-derives the seeding from scratch.
- [X] T060 Run the whole suite: `./gradlew test connectedAndroidTest`. Everything green, including every test `002` and `003` shipped.
- [X] T061 Manual smoke check on a **fresh install in airplane mode**: `./gradlew :app:installDebug`, then (1) open the app — tasks appear and the streak reads an unstarted record as an invitation, not as zero failures; (2) complete one task — the run reads 1 immediately without leaving the screen; (3) undo it — the run returns to 0 immediately with nothing implying a mistake; (4) complete one task, kill the app, relaunch — the run reads 1, recomputed from the record; (5) step through the prayer blocks — the element does not move and does not change. Report any step that does not behave as described rather than adjusting the app to match.
- [X] T062 Verify the merge gate before opening the PR into `develop-v1`: the Constitution Check in [plan.md](./plan.md) passes; test commits precede their implementation commits **in this PR's history**, which is the only place that evidence survives a squash merge; persistence and the catalogue are untouched, with `StreakImmutabilityTest` (T031) standing in for Principle III's test obligation; and `docs/GLOSSARY.md` carries both new terms.

---

## Dependencies

```text
Phase 1 (Setup)
   └─> Phase 2 (Foundational) ── blocks everything below
          ├─> Phase 3 (US1, P1) 🎯 MVP ── the only phase that must ship
          │      ├─> Phase 5 (US3, P2)  needs StreakSummary and the element from US1
          │      └─> Phase 6 (US4, P3)  needs StreakSummary and the element from US1
          └─> Phase 4 (US2, P1)  needs the fold from US1 (T013) but nothing from US3 or US4
                 └─> Phase 7 (Polish)
```

- **US1 is the only hard prerequisite.** US2, US3 and US4 each extend it and none depends on another.
  US2's T028 refactors one line inside `BuildStreakSummary.kt` to call `isConsistencyDay`; that is a
  change to a US1 file, not a dependency US3 or US4 acquires.
- **US3 and US4 may be built in either order**, or in parallel by two people. Both modify
  `StreakSummary.kt`, `BuildStreakSummary.kt`, `TodayUiState.kt`, `TodayViewModel.kt` and
  `StreakElement.kt`, so doing them at the same time in one working copy will conflict — take one at
  a time unless the work is genuinely split across branches.
- **Phase 7 needs all four stories** only for T057 and T060. T056 (the glossary) can be done at any
  point after Phase 1.

## Parallel execution examples

Within Phase 2, after T005 lands:

```text
T006 (domain fake) and T007 (app fake) — different modules, no shared file
```

Within Phase 3, the test-authoring tasks are independent of each other:

```text
T011 (fold test) ‖ T015 (use case + clock test) ‖ T018 (ViewModel test) ‖ T023 ‖ T023a (Compose tests)
```

…but their implementation tasks are strictly sequential, because each builds on the previous type.

Within Phase 4, all four test tasks touch different files:

```text
T027 (domain) ‖ T029 (domain test) ‖ T030 (data) ‖ T031 (data)
```

Within Phase 6, the two test tasks are independent:

```text
T045 (StreakClock test) ‖ T047 (at-risk fold test) ‖ T049 (boundary re-emission test)
```

Within Phase 7:

```text
T056 (glossary) ‖ T057 (audit) ‖ T058 (write check)
```

## Implementation strategy

**MVP is Phase 1 + Phase 2 + Phase 3.** At the end of T026 the app shows a correct current streak and
longest streak that survive a missing catalogue, a failed read, and process death. That is a
shippable increment and satisfies the roadmap's Phase 4 goal.

**Then, in order of value**: Phase 4 (proves the criterion — cheap, and it is what stops a wrong
number reaching the user), Phase 5 (the indicator and the break framing), Phase 6 (the nudge).

**If you must stop early**, stop at a checkpoint, never mid-phase. Every checkpoint leaves the app
green and shippable. Three places inside a phase are not safe stopping points, because each is one
change split across two tasks: T019–T021 (the constructor and DI change must land together or the app
will not start), T036–T037, and T048a–T048b.

**Commit boundaries matter as much as task boundaries.** The merge gate reads the PR's history, so
every test task must be its own commit, landed before the implementation it justifies. Where two
implementation tasks are marked "commit together" (T036/T037, T048a/T048b), one commit for the pair
is correct — the split exists to keep the ordering legible, not to multiply commits.

**The riskiest task is T020.** It changes shipped behaviour in `TodayViewModel`. The existing
`TodayViewModelTest` cases are the regression net for the day's tasks, scoring, section position and
rollover; if one of them goes red, the restructure went further than it should have. Nothing about
how the day works is meant to change — only that a status transition now preserves one field beside
it.
