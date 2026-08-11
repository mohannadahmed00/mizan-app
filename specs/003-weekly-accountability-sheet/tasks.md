---

description: "Task list for 003-weekly-accountability-sheet"
---

# Tasks: Weekly Accountability Sheet

**Input**: Design documents from `/specs/003-weekly-accountability-sheet/`

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

`002` is **already built and merged**. This feature extends working code. That means:

1. **Never create a file that already exists.** Check first. Most tasks below say CREATE or MODIFY —
   obey the verb.
2. **Never add an update method to `DayPlanDao` or `DayPlanRepository`.** A day plan is written once.
   If a task seems to need one, the task is being misread.
3. **Every completion query filters `reversedAt IS NULL AND deletedAt IS NULL`.** Forgetting this
   inflates a past week's total. Copy the filter from existing queries in `CompletionDao.kt`.
4. **No code outside `TimeProvider` may read a clock.** No `LocalDate.now()`, no `Instant.now()`, no
   `System.currentTimeMillis()`. Not in tests either — use `TestTimeProvider`.
5. **`:domain` is a pure Kotlin JVM module.** Any `import android.*` there will not compile. That is
   intentional.
6. **Composables are not exempt from test-first.** The constitution exempts only DI module wiring,
   `@Preview` composables, and generated code — screens are not on that list. Every `WeekScreen` and
   `DaySummaryScreen` task is preceded by a Compose UI test task. `androidx-compose-ui-test-junit4`
   and `ui-test-manifest` are already dependencies of `:app`, so no build change is needed; the
   tests live in `app/src/androidTest/java/…` and run with `./gradlew :app:connectedDebugAndroidTest`.

### Where tests live

| Module | Source set | Runs with |
|---|---|---|
| `:domain` | `domain/src/test/kotlin/…` | `./gradlew :domain:test` (JVM, fast) |
| `:data` | `data/src/androidTest/kotlin/…` | `./gradlew :data:connectedDebugAndroidTest` (needs a device) |
| `:app` ViewModels | `app/src/test/java/…` | `./gradlew :app:testDebugUnitTest` (JVM) |
| `:app` Compose screens | `app/src/androidTest/java/…` | `./gradlew :app:connectedDebugAndroidTest` (needs a device) |

### Fixture dates used throughout (verified real weekdays)

| Date | Weekday | Use |
|---|---|---|
| `2026-08-08` | Saturday | start of the standard test week |
| `2026-08-14` | Friday | end of the standard test week |
| `2026-08-11` | Tuesday | "today" for mid-week tests |
| `2026-12-26` | Saturday | start of the week crossing month **and** year |
| `2027-01-01` | Friday | end of that week |
| `2026-03-14` | Saturday | `TestTimeProvider`'s existing default date |

### The numbers every test asserts against

Per-day available points, Saturday → Friday: **69, 69, 74, 69, 69, 74, 76**. Week total **500**.

Cumulative (elapsed available as the week progresses): **69, 138, 212, 281, 350, 424, 500**.

If any of these come out differently, stop and report it. `001` validates the catalogue against
these figures — the arithmetic is not the thing to adjust.

---

## Phase 1: Setup

**Purpose**: Confirm the starting point. No code changes.

- [X] T001 Confirm the working branch is `spec/003-weekly-accountability-sheet` by running `git branch --show-current` at the repository root. If it is not, stop and report — do not create the branch yourself.
- [X] T002 Establish a green baseline by running `./gradlew :domain:test :app:testDebugUnitTest` at the repository root. All tests must pass before any change is made. If any test already fails, stop and report which one — do not start work on a red baseline.

**Checkpoint**: Baseline green, correct branch.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The week rule, the schema change, and the range queries. Every user story depends on
all three.

**⚠️ CRITICAL**: No user story work may begin until Phase 2 is complete.

### 2a — The week rule (`:domain`, pure Kotlin)

- [X] T003 [P] CREATE the failing test `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/WeekBoundaryTest.kt`. Use JUnit 4 (`org.junit.Test`, `org.junit.Assert.assertEquals`). Assert: (a) `WeekBoundary.startOfWeek(LocalDate.parse("2026-08-08"))` returns `2026-08-08` — a Saturday is the start of its own week; (b) `startOfWeek(LocalDate.parse("2026-08-14"))` returns `2026-08-08` — a Friday belongs to the week that began the previous Saturday; (c) `startOfWeek` returns `2026-08-08` for every one of the seven dates `2026-08-08` through `2026-08-14`; (d) `startOfWeek(LocalDate.parse("2026-08-15"))` returns `2026-08-15` — the next Saturday starts a new week; (e) `WeekBoundary.weekContaining(LocalDate.parse("2026-12-30")).dates` equals exactly the seven dates `2026-12-26` through `2027-01-01` in order, proving a week may cross both a month and a year boundary; (f) for all seven dates in that week, `weekContaining(date).key` is the same `WeekKey("2026-12-26")`. Run `./gradlew :domain:test` and confirm it fails to compile — that is the expected first failure.
- [X] T004 CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/Week.kt` in package `com.giraffe.mizanapp.domain.week`. Define `@JvmInline value class WeekKey(val value: String)` and `data class Week(val key: WeekKey, val start: LocalDate, val dates: List<LocalDate>)`. In `Week`'s `init` block, `require(dates.size == 7)`, `require(dates.first() == start)`, and `require(start.dayOfWeek == DayOfWeek.SATURDAY)`. Add `val end: LocalDate get() = dates.last()`. Import `java.time.DayOfWeek` and `java.time.LocalDate`.
- [X] T005 CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/WeekBoundary.kt` in package `com.giraffe.mizanapp.domain.time`, as `object WeekBoundary`. Implement `fun startOfWeek(date: LocalDate): LocalDate = date.minusDays(((date.dayOfWeek.value + 1) % 7).toLong())` — this formula maps SATURDAY→0, SUNDAY→1, MONDAY→2 … FRIDAY→6, which is exactly the Saturday-to-Friday rule. Implement `fun weekContaining(date: LocalDate): Week` building `Week(key = WeekKey(start.toString()), start = start, dates = (0L..6L).map { start.plusDays(it) })`. Add a KDoc stating that this is the single place the week rule exists (FR-001, Principle VII) and that no other code may compute a week start or key. Run `./gradlew :domain:test` — T003 must now pass.

### 2b — Day Plan origin and the Room migration

> This is the only schema change in the feature. Do 2b in order; the tasks are dependent.

- [X] T006 CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/MizanDatabaseMigrationTest.kt`. Use `androidx.room.testing.MigrationTestHelper` with `InstrumentationRegistry.getInstrumentation()` and `MizanDatabase::class.java`, declared as a JUnit `@get:Rule`. The test must: create a version-1 database named `"migration-test.db"`; insert one `day_plans` row by raw SQL with `id='p1'`, `date='2026-08-08'`, `catalogueVersion=1`, `hijriLabel='X'`, `availablePoints=69`, `updatedAt=1`; insert one matching `completions` row with `id='c1'`, `dayPlanId='p1'`, `taskSlug='fajr-1'`, `creditedDate='2026-08-08'`, `pointsAwarded=2`, `recordedAt=1`, `updatedAt=1`; close it; run the migration with `helper.runMigrationsAndValidate("migration-test.db", 2, true, MIGRATION_1_2)`; then query the migrated database and assert `availablePoints` is still `69`, `hijriLabel` is still `'X'`, the completion's `pointsAwarded` is still `2`, and the new `origin` column reads `'OPENED'`. Run `./gradlew :data:connectedDebugAndroidTest` and confirm it fails.
- [X] T007 CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/PlanOrigin.kt` in package `com.giraffe.mizanapp.domain.day` containing `enum class PlanOrigin { OPENED, BACKFILLED }`. Add a KDoc: `OPENED` means the app was running on that date; `BACKFILLED` means the plan was created afterwards for a date the user never saw; Phase 4's streak rule depends on the distinction (FR-011).
- [X] T008 MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/DayPlan.kt`. Add `val origin: PlanOrigin` as the last constructor parameter with **no default value** — a default would let a caller forget it. Do not change any other field, and do not add any method that mutates the class.
- [X] T009 MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/BuildDayPlan.kt`. Add an `origin: PlanOrigin` parameter to `buildDayPlan(...)` and pass it into the returned `DayPlan`. Place it after `date` and before `newId`. Do not give it a default.
- [X] T010 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/DayEntities.kt`. Add `val origin: String = "OPENED"` to `DayPlanEntity` as the last property. Store it as `TEXT` so the exported schema is readable in a diff. Do not touch `PlannedTaskEntity` or `CompletionEntity`.
- [X] T011 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/mapper/Mappers.kt`. In `DayPlan.toEntity(updatedAt)` add `origin = origin.name`. In `DayPlanWithTasks.toDomain()` add `origin = PlanOrigin.valueOf(plan.origin)`. Import `com.giraffe.mizanapp.domain.day.PlanOrigin`. Both directions are required — a mapper that drops the field is exactly the bug the file's own KDoc warns about.
- [X] T012 CREATE `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Migrations.kt` in package `com.giraffe.mizanapp.data.db`. Define `val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE day_plans ADD COLUMN origin TEXT NOT NULL DEFAULT 'OPENED'") } }`. Import `androidx.room.migration.Migration` and `androidx.sqlite.db.SupportSQLiteDatabase`. Add a KDoc explaining that the `'OPENED'` default is a fact and not a guess: `002` creates a plan only for the current date at launch, so no plan in a v1 database can be a backfill (FR-013e). **This migration must remain purely additive — never drop, rename, or rewrite a column.**
- [X] T013 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`. Change `version = 1` to `version = 2`. Change nothing else in the annotation.
- [X] T014 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabaseFactory.kt`. Add `.addMigrations(MIGRATION_1_2)` to the `Room.databaseBuilder(...)` chain before `.build()`. Do NOT add `fallbackToDestructiveMigration()` anywhere — a destructive migration is forbidden by the constitution and would silently erase user history.
- [X] T015 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomDayPlanRepository.kt`. In `ensurePlanFor`, determine the origin before building the plan: `val origin = if (date == time.today()) PlanOrigin.OPENED else PlanOrigin.BACKFILLED`, and pass it to `buildDayPlan`. The caller never chooses — the repository decides from the date and the injected clock, so two callers cannot label the same situation differently (contracts/repositories.md guarantee 5).
- [X] T016 Run `./gradlew :data:assembleDebug` at the repository root to generate `data/schemas/2.json`, then confirm the file exists and `git add` it. A missing exported schema blocks the `develop-v1` → `main` release gate. Do not edit the generated file by hand.
- [X] T017 MODIFY every existing test that constructs a `DayPlan` or calls `buildDayPlan` so the project compiles again: `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/DayFixtures.kt`, `BuildDayPlanTest.kt`, `ScoreDayTest.kt`, `LandingSectionTest.kt`, `RolloverTest.kt`, and `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt`. Pass `PlanOrigin.OPENED` in each. Find them all with `grep -rn "buildDayPlan\|DayPlan(" domain/src/test app/src/test`. Change assertions in these files only where the compiler forces it — do not weaken any existing assertion.
- [X] T018 Run `./gradlew :domain:test :app:testDebugUnitTest :data:connectedDebugAndroidTest`. T006 must now pass, and every test that passed at T002 must still pass. If an existing `002` test now fails, the cause is in T007–T017 — fix it there, never by changing the old test's expectations.

### 2c — Catalogue version resolution (research.md R1)

- [X] T019 [P] CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/CatalogueVersionResolutionTest.kt`, extending `DbTestBase`. Seed the catalogue with `catalogue.seedIfNeeded()`. The shipped seed declares version 1 with `effectiveFrom = "2026-01-01"`. Assert: (a) `catalogue.versionEffectiveOn(LocalDate.parse("2026-08-08"))` returns `1`; (b) `catalogue.versionEffectiveOn(LocalDate.parse("2025-06-01"))` — a date **before** the seed's effective-from — also returns `1`, because the earliest version applies open-ended backwards (FR-013b); (c) on a database where `seedIfNeeded()` has NOT been called, `versionEffectiveOn` of any date returns `null`, because no catalogue at all is a genuine absence. Use `kotlinx.coroutines.test.runTest`. Run `./gradlew :data:connectedDebugAndroidTest` and confirm case (b) fails.
- [X] T020 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CatalogueDao.kt`. Replace the body of the `versionEffectiveOn` query with `SELECT COALESCE((SELECT MAX(version) FROM catalogue_versions WHERE effectiveFrom <= :date), (SELECT MIN(version) FROM catalogue_versions))`. Update the KDoc: the greatest version whose effective-from is on or before the date; the lowest version when every version starts later, because a catalogue applies until superseded and the earliest has nothing before it to defer to; null only when no version exists at all. Run `./gradlew :data:connectedDebugAndroidTest` — T019 must now pass.

### 2d — Range queries

- [X] T021 [P] CREATE the failing test `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RangeQueryTest.kt`, extending `DbTestBase`. Seed the catalogue. Use `time.setDate(...)` to move the clock and `dayPlans.ensurePlanFor(...)` to create plans for `2026-08-08`, `2026-08-10` and `2026-08-12` only. Assert: (a) `dayPlans.plansBetween(LocalDate.parse("2026-08-08"), LocalDate.parse("2026-08-14"))` returns exactly 3 plans, in ascending date order, and does NOT fabricate entries for the missing dates; (b) the range is inclusive at both ends — a range of `2026-08-08` to `2026-08-08` returns exactly 1 plan; (c) `dayPlans.earliestPlanDate()` returns `2026-08-08`; (d) on an empty database `earliestPlanDate()` returns `null`; (e) **set the clock to `2026-08-10` with `time.setDate(LocalDate.parse("2026-08-10"))` first**, then record a completion on `2026-08-10` and undo it, and assert `completions.liveBetween(...)` over the week returns an empty list — the tombstoned row must not appear. The clock move in (e) is mandatory: `DayWritePolicy` permits writes only on `time.today()`, so without it `record` returns `NotWritable` and the assertion is unreachable. Run and confirm it fails to compile.
- [X] T022 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/DayPlanDao.kt`. Add two queries. First: `@Transaction @Query("SELECT * FROM day_plans WHERE date BETWEEN :start AND :end AND deletedAt IS NULL ORDER BY date") suspend fun plansBetween(start: String, end: String): List<DayPlanWithTasks>`. Second: `@Query("SELECT MIN(date) FROM day_plans WHERE deletedAt IS NULL") suspend fun earliestPlanDate(): String?`. Add no other method — in particular no update method of any kind.
- [X] T023 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CompletionDao.kt`. Add `@Query("SELECT * FROM completions WHERE creditedDate BETWEEN :start AND :end AND reversedAt IS NULL AND deletedAt IS NULL ORDER BY creditedDate, recordedAt") suspend fun liveBetween(start: String, end: String): List<CompletionEntity>`. The two null filters are not optional — copy them exactly. A range read that returned tombstones would inflate a past week's earned total.
- [X] T024 MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/DayPlanRepository.kt`. Add `suspend fun plansBetween(start: LocalDate, end: LocalDate): List<DayPlan>` and `suspend fun earliestPlanDate(): LocalDate?` to the interface, with the KDoc from contracts/repositories.md guarantees 7 and 8. Add no update method.
- [X] T025 MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CompletionRepository.kt`. Add `suspend fun liveBetween(start: LocalDate, end: LocalDate): List<Completion>` to the interface, with a KDoc noting it returns live records only.
- [X] T026 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomDayPlanRepository.kt` to implement `plansBetween` (map each `DayPlanWithTasks` with the existing `toDomain()`) and `earliestPlanDate` (map the nullable `String` with `LocalDate.parse`). Convert `LocalDate` to `String` with `.toString()` at the DAO boundary, matching the existing `planFor`.
- [X] T027 MODIFY `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCompletionRepository.kt` to implement `liveBetween`, mapping entities with the existing `toDomain()`.
- [X] T028 MODIFY `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt` to implement the three new interface methods on the existing fakes, backed by their in-memory maps. Keep the tombstone filter in the fake `liveBetween` — a fake that behaves differently from the real query hides the bug it was meant to catch.
- [X] T029 Run `./gradlew :domain:test :app:testDebugUnitTest :data:connectedDebugAndroidTest`. Everything must pass.

**Checkpoint**: Week rule exists, schema is at v2 with the origin column, range queries work. User story work can begin.

---

## Phase 3: User Story 1 — Read the week (Priority: P1) 🎯 MVP

**Goal**: A Saturday-to-Friday sheet showing each day's earned against available, plus the week's
own figures, reachable from Today.

**Independent Test**: Seed a week where every day was opened, open the sheet, and check every
per-day figure and the week total by hand against the seeded records.

**Scope note**: this phase aggregates **plans that already exist**. Days the app never opened are
Phase 4's job. Do not write backfill here.

### Tests for User Story 1 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T030 [P] [US1] CREATE `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/WeeklyScoreTest.kt`. Assert the invariants: `WeeklyScore(earned = 0, elapsedAvailable = 0, weekTarget = 500).fraction` is `0f` and does not divide by zero; `WeeklyScore(120, 281, 500).fraction` equals `120f / 281f` — that is, **`fraction` divides by `elapsedAvailable`, never by `weekTarget`** (FR-009a); constructing with `earned` negative, with `earned > elapsedAvailable`, or with `elapsedAvailable > weekTarget` throws `IllegalArgumentException`.
- [X] T031 [P] [US1] CREATE `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummaryTest.kt` — the most important test in the feature. Build a `Week` for `2026-08-08`. Using in-memory `DayPlan` and `Completion` fixtures (extend the existing `DayFixtures.kt` helpers): (a) **the 500 fixture** — seven plans with available 69, 69, 74, 69, 69, 74, 76 and every task completed to its limit, with "today" set to `2026-08-14`, produces `earned = 500`, `elapsedAvailable = 500`, `weekTarget = 500`, `fraction = 1.0f`; (b) with nothing completed, `earned = 0`, `elapsedAvailable = 500`, and every day's state is `NOTHING_RECORDED`; (c) with "today" at `2026-08-11` (Tuesday) and plans for Sat–Tue only, `elapsedAvailable` is `281` and `weekTarget` is `500`; (d) the seven cumulative elapsed-available values across the week are exactly `69, 138, 212, 281, 350, 424, 500`; (e) `days` always has exactly 7 entries whatever is missing; (f) day states resolve correctly — `earned == 0` → `NOTHING_RECORDED`, `0 < earned < available` → `PARTLY_RECORDED`, `earned == available` → `FULLY_RECORDED`, date after today → `NOT_YET_ELAPSED`, date before record start → `OUTSIDE_RECORD`; (g) a tombstoned completion contributes 0 earned points; (h) **repeat assertions (a) and (d) against the week beginning `2026-12-26` and ending `2027-01-01`** — the same per-day figures 69, 69, 74, 69, 69, 74, 76 and the same cumulative sequence must hold across a week that crosses both a month and a year boundary (SC-002). Note the weekday alignment is identical because the week always starts on a Saturday, so the expected numbers do not change — that is the property being proved.
- [X] T032 [P] [US1] CREATE `app/src/test/java/com/giraffe/mizanapp/week/WeekViewModelTest.kt`. Using the existing fakes from `FakeRepositories.kt` and a fake `TimeProvider`, assert: the initial state is `Status.Loading`; after loading a seeded week the state is `Status.Ready` with 7 `DayCellUi` entries; `earnedPoints`, `elapsedAvailablePoints` and `weekTargetPoints` match the fixture; `progressFraction` divides by `elapsedAvailablePoints`; when the catalogue has never been seeded the state is `Status.CatalogueUnavailable` and NOT an empty week.

### Implementation for User Story 1

- [X] T033 [P] [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/DayCellState.kt` in package `com.giraffe.mizanapp.domain.week` containing `enum class DayCellState { OUTSIDE_RECORD, NOT_YET_ELAPSED, NOTHING_RECORDED, PARTLY_RECORDED, FULLY_RECORDED }`. KDoc each value, and state plainly that none of them is a failure state (Principle IX) — in particular `NOTHING_RECORDED` is a neutral fact, not a miss.
- [X] T034 [P] [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/WeeklyScore.kt`. Define `data class WeeklyScore(val earned: Int, val elapsedAvailable: Int, val weekTarget: Int)` with an `init` block requiring `earned >= 0`, `earned <= elapsedAvailable`, and `elapsedAvailable <= weekTarget`. Add `val fraction: Float get() = if (elapsedAvailable == 0) 0f else earned.toFloat() / elapsedAvailable`. Add a KDoc stating explicitly that `fraction` must never divide by `weekTarget` — that single choice is what stops a Sunday morning reading as 10% of a week.
- [X] T035 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/WeekSummary.kt`. Define `data class DayCell(val date: LocalDate, val hijriLabel: String?, val earned: Int, val available: Int, val state: DayCellState)` and `data class WeekSummary(val week: Week, val score: WeeklyScore, val days: List<DayCell>)` with `require(days.size == 7)` in `WeekSummary`'s `init`. `hijriLabel` is null only for `OUTSIDE_RECORD` and `NOT_YET_ELAPSED`, which have no stored plan to take one from.
- [X] T036 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/ProjectAvailablePoints.kt`. Implement `fun projectAvailablePoints(catalogue: Catalogue, version: Int, date: LocalDate): Int` as `resolveApplicableTasks(catalogue, version, date).sumOf { it.points * it.maxOccurrencesPerDay }`. This reuses `002`'s applicability rule so a projected Monday and a materialised Monday cannot disagree. **It must return an `Int` and persist nothing** (FR-009d).
- [X] T037 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt` — a pure function, no repositories, no clock. Signature: `fun buildWeekSummary(week: Week, today: LocalDate, recordStart: LocalDate?, plans: List<DayPlan>, completions: List<Completion>, projectedAvailable: Map<LocalDate, Int>): WeekSummary`. For each of the week's seven dates, find its plan and its completions (match on `creditedDate`), compute earned as the sum of `pointsAwarded` over records where `isLive` is true, and derive the state using the table in data-model.md Part 1. Sum `elapsedAvailable` over dates `<= today` that have a plan; sum `weekTarget` as `elapsedAvailable` plus `projectedAvailable` for dates `> today`. Run `./gradlew :domain:test` — T030 and T031 must now pass.
- [X] T038 [US1] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummary.kt` in package `com.giraffe.mizanapp.domain.usecase`, with the constructor and `WeekOutcome` sealed interface exactly as given in [contracts/repositories.md](./contracts/repositories.md). **In this phase implement the aggregation path only**: read `plansBetween`, `liveBetween`, `earliestPlanDate`, and `today` from `TimeProvider`; project available points for dates after today via `catalogue.currentVersion()` + `catalogue.catalogueAt(...)` + `projectAvailablePoints`; call `buildWeekSummary`; return `Ready`. Return `NoCatalogue` when `currentVersion()` is null. Leave a `// Phase 4 (US2): backfill goes here, before aggregation` comment where backfill will be inserted — do not implement it now.
- [X] T039 [P] [US1] CREATE `app/src/main/java/com/giraffe/mizanapp/week/WeekUiState.kt` exactly as specified in [contracts/ui-state.md](./contracts/ui-state.md), including `DayCellUi`, the four `Status` values, and the `WeekEvent` sealed interface with its four cases. **Add no event that could complete, undo, add, remove, reorder or reprice anything** — its absence is Principle VI made structural, as `002` did for `TodayEvent`.
- [X] T040 [US1] CREATE `app/src/main/java/com/giraffe/mizanapp/week/WeekViewModel.kt`, following the shape of `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt`: a private `MutableStateFlow` exposed as an immutable `StateFlow`, no mutable state escaping, no clock read except through the injected `TimeProvider`. Constructor takes `GetWeekSummary`, `CatalogueRepository` and `TimeProvider`. **Use the `CatalogueRepository` for exactly one thing**: call `catalogue.seedIfNeeded()` first on load and map `SeedOutcome.Failed` to `Status.CatalogueUnavailable`, exactly as `TodayViewModel` does. This is why the dependency exists — the week screen must not depend on the Today screen having run first. Hold the viewed week in a private field initialised to `WeekBoundary.weekContaining(time.today())` — **never persist it** (FR-019). Map `WeekOutcome.Ready` to `Status.Ready`, `NoCatalogue` to `Status.CatalogueUnavailable`. Leave `canGoPrevious`/`canGoNext` as `false` for now; Phase 6 fills them in. Run `./gradlew :app:testDebugUnitTest` — T032 must pass.
- [X] T040a [P] [US1] CREATE the failing UI test `app/src/androidTest/java/com/giraffe/mizanapp/week/WeekScreenTest.kt`. Use `androidx.compose.ui.test.junit4.createComposeRule()` as a JUnit `@get:Rule` — `androidx-compose-ui-test-junit4` and `ui-test-manifest` are already dependencies of `:app`, so no build change is needed. Set the content to `WeekScreen(state = <a fixed WeekUiState>, onEvent = { recorded += it })`. Assert: (a) with a `Ready` state, seven day cells are displayed and the Saturday cell appears before the Friday cell; (b) the headline shows earned against `elapsedAvailablePoints`, and the node showing `weekTargetPoints` is a different node — the two figures are never rendered as one fraction; (c) tapping a cell whose `isOpenable` is true emits exactly one `WeekEvent.OpenDay` carrying that date; (d) tapping a cell in `OUTSIDE_RECORD` or `NOT_YET_ELAPSED` emits nothing; (e) no rendered text contains a minus sign, and none contains "missed", "failed", "behind" or "to go" (FR-016). Run `./gradlew :app:connectedDebugAndroidTest` and confirm it fails.
- [X] T041 [US1] CREATE `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt` — a stateless `@Composable` taking `state: WeekUiState` and `onEvent: (WeekEvent) -> Unit`. Render seven day cells in Saturday-to-Friday order, each with its English day label, its Hijri label when present, and `earned / available`. Render the week's earned against `elapsedAvailablePoints` as the headline, with `weekTargetPoints` shown separately and clearly subordinate. Use the design tokens from `CLAUDE.md` and the existing `app/src/main/java/com/giraffe/mizanapp/ui/theme/Color.kt`. **No red, no cross, no negative figure, and no "behind by" or "X to go" phrasing anywhere** (FR-016, FR-009b). Run `./gradlew :app:connectedDebugAndroidTest` — T040a must pass.
- [X] T042 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` **and** `app/src/main/java/com/giraffe/mizanapp/today/TodayScreen.kt`. In `MainActivity.kt`: add `sealed interface Destination { data object Today; data object Week; data class DaySummary(val date: LocalDate) }`, hold the current destination in `rememberSaveable`, render `TodayRoute` or a new `WeekRoute` accordingly, and handle system back so `Week` returns to `Today`. Add no navigation dependency (research.md R3). In `TodayScreen.kt`: add a single header action that opens the week, exposed as a new `onOpenWeek: () -> Unit` parameter rather than as a `TodayEvent` — navigation is the host's concern, and adding a case to `TodayEvent` would widen a sealed type whose narrowness is deliberate.
- [X] T043 [US1] MODIFY `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`. In `domainModule` add `factory { GetWeekSummary(get(), get(), get(), get()) }`. In `appModule` add `viewModel { WeekViewModel(get(), get(), get()) }`. This wiring is the one thing exempt from test-first.

**Checkpoint**: The week sheet renders correct figures for days that were opened. US1 is independently demonstrable.

---

## Phase 4: User Story 2 — Skipped days still count (Priority: P1)

**Goal**: A date the app never opened appears as 0 out of its correct available total rather than
vanishing from the week.

**Independent Test**: With a fake clock, record on Saturday, advance to Thursday without launching,
open the sheet — Sunday through Wednesday each read 0 out of 69/69/74/69, and the week's available
total is the same as if the app had been opened every day.

### Tests for User Story 2 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T044 [P] [US2] CREATE `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummaryBackfillTest.kt` using fake repositories. Assert: (a) an elapsed date in the viewed week with no plan gets exactly one plan created; (b) a date **at or after today** never gets a plan (FR-010c); (c) a date **before the record start** never gets a plan (FR-012); (d) an existing plan is returned untouched and is never rebuilt (FR-010b); (e) invoking twice for the same week creates no second plan and returns identical figures (FR-010d); (f) backfill creates **no completion at all** (FR-011a); (g) when `ensurePlanFor` fails, the result is `WeekOutcome.BackfillFailed` and **no** figures are returned (FR-014b); (h) plans written before a failure survive and are reused on the next invocation (FR-014d).
- [X] T045 [P] [US2] CREATE `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BackfillOriginTest.kt`, extending `DbTestBase`. Set the clock to `2026-08-08`, seed, and let `ensurePlanFor` create that day's plan. Advance the clock to `2026-08-13` with `time.setDate(...)`. Call `dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10"))`. Assert the resulting plan's `origin` is `BACKFILLED`, the `2026-08-08` plan's origin is still `OPENED`, and the backfilled Monday `2026-08-10` has `availablePoints == 74` — the voluntary fast is present because the schedule rule says so, not because anyone was there to see it.
- [X] T046 [US2] CREATE `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoricalImmutabilityTest.kt`, extending `DbTestBase`. **This test is required by constitution Principle III and the increment is not complete without it.** Steps, in order: (1) `catalogue.seedIfNeeded()` — this is catalogue version 1, effective from `2026-01-01`; (2) with the clock at `2026-08-08` create an `OPENED` plan, then move the clock to `2026-08-13` and `ensurePlanFor(LocalDate.parse("2026-08-10"))` to create a `BACKFILLED` plan; (3) introduce catalogue version 2 with **two** DAO calls — `catalogueDao().insertVersions(listOf(CatalogueVersionEntity(version = 2, effectiveFrom = "2026-09-01")))` **and** `catalogueDao().insertTaskVersions(...)` carrying the same task slugs with **different** `points` and a **different** `scheduleType`/`scheduleDays`, each row with `catalogueVersion = 2` and a fresh UUID `id`. Inserting the version row alone changes nothing, because points live on `task_versions`; (4) assert both stored plans still report their original tasks, their original per-task points and their original `availablePoints`, and that every existing completion still carries its original `pointsAwarded`; (5) move the clock to a date **on or after `2026-09-01`** — use `2026-09-05` — and `ensurePlanFor` it, then assert that plan reflects version 2. The date in (5) must not be earlier than v2's `effectiveFrom` or `versionEffectiveOn` correctly resolves back to version 1 and the assertion will fail for the wrong reason.

### Implementation for User Story 2

- [X] T047 [US2] MODIFY `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummary.kt`, replacing the Phase 3 placeholder comment. Before any aggregation, compute `recordStart = plans.earliestPlanDate()`; if it is null skip backfill entirely. Otherwise, for each date in `week.dates` where `date < today && date >= recordStart`, call `plans.ensurePlanFor(date)`. Treat `EnsureOutcome.Created` and `AlreadyExists` as success — a concurrent creation losing the race on the unique index is "someone else created it", not a failure. Treat `NoCatalogue`, or any thrown storage error, as failure: return `WeekOutcome.BackfillFailed(week)` immediately and compute no figures. Only when every required date has a plan may aggregation proceed. Run `./gradlew :domain:test` — T044 must pass.
- [X] T048 [US2] MODIFY `app/src/main/java/com/giraffe/mizanapp/week/WeekViewModel.kt` to map `WeekOutcome.BackfillFailed` to `Status.CouldNotLoad`, and handle `WeekEvent.Retry` by re-invoking `GetWeekSummary` for the same week. The state must carry **no** day figures while `CouldNotLoad` — never a week rendered with the unfillable days omitted, and never a total over an incomplete set of days.
- [X] T048a [US2] CREATE the failing UI test `app/src/androidTest/java/com/giraffe/mizanapp/week/WeekFailureStateTest.kt` using `createComposeRule()`. Render `WeekScreen` with `status = Status.CouldNotLoad(...)`. Assert: (a) **no day cell is displayed at all** — assert the Saturday and Friday labels do not exist; (b) no earned or target figure is displayed, so no total over an incomplete set can be read; (c) a retry action exists and tapping it emits exactly one `WeekEvent.Retry`; (d) the message text contains none of "you", "your", "missed", "failed" — it attributes the failure to the app, not the user (FR-014c). Run and confirm it fails.
- [X] T049 [US2] MODIFY `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt` to render the `CouldNotLoad` status as a plain message with a retry action. The wording must attribute the failure to the app, never to the user, and must say nothing about what was or was not recorded on the affected dates (FR-014c). Do not render any day cell in this state. Run `./gradlew :app:connectedDebugAndroidTest` — T048a must pass.
- [X] T050 [US2] Run `./gradlew :domain:test :app:testDebugUnitTest :data:connectedDebugAndroidTest`. T044, T045 and T046 must all pass.

**Checkpoint**: Skipped days appear correctly, and a recorded day's figures survive a catalogue change. US1 + US2 together are the honest weekly sheet.

---

## Phase 5: User Story 3 — Look into a day (Priority: P2)

**Goal**: Tapping a day cell opens that date as it was — its tasks, what was completed, and the
points each carried. Nothing on the screen can change it.

**Independent Test**: Open a recorded past day from the sheet, change the catalogue's points and
schedule, reopen the same day — every task, point value and total is unchanged.

### Tests for User Story 3 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T051 [P] [US3] CREATE `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetDaySummaryTest.kt` using fake repositories. Assert: a date with a plan returns a summary whose tasks, labels, points and occurrence limits come from the **stored plan** and not from the catalogue; a date with no plan returns `null` and **no plan is created**; occurrence counts exclude tombstoned completions; sections appear in the plan's own `sectionOrder` and tasks within a section in `displayPosition` order.
- [X] T052 [P] [US3] CREATE `app/src/test/java/com/giraffe/mizanapp/daysummary/DaySummaryViewModelTest.kt`. Assert the initial state is `Loading`; a recorded date yields `Ready` with the correct earned and available figures; a date with no plan yields `Status.NoRecord` and not an error.

### Implementation for User Story 3

- [X] T053 [P] [US3] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/DaySummary.kt`. Define `data class PlannedTaskRecord(val task: PlannedTask, val recordedCount: Int)` and `data class DaySummary(val date: LocalDate, val hijriLabel: String, val score: DailyScore, val state: DayCellState, val tasks: List<PlannedTaskRecord>)`.
- [X] T054 [US3] CREATE `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetDaySummary.kt` with the constructor and guarantees from [contracts/repositories.md](./contracts/repositories.md). Build entirely from `plans.planFor(date)` and `completions.observeCompletions`/`liveBetween` for that single date. Use the existing `liveCount(completions, taskSlug)` from `domain/day/Occurrences.kt` for occurrence counts and the existing `scoreDay(plan, completions)` for the score. **Never consult the catalogue**, and never call `ensurePlanFor` — return `null` when there is no plan.
- [X] T055 [P] [US3] CREATE `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryUiState.kt` exactly as specified in [contracts/ui-state.md](./contracts/ui-state.md). Note two deliberate absences: `SummaryTaskUi` has **no `canUndo`** field, unlike `002`'s `TaskRowUi`, and this screen has **no event type at all**. Both absences are the type-level statement that this screen cannot write (FR-024).
- [X] T056 [US3] CREATE `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryViewModel.kt` following the `TodayViewModel` shape. Constructor takes `GetDaySummary` and the `LocalDate` to show. Expose one immutable `StateFlow`. It must have no method that writes anything.
- [X] T056a [P] [US3] CREATE the failing UI test `app/src/androidTest/java/com/giraffe/mizanapp/daysummary/DaySummaryScreenTest.kt` using `createComposeRule()`. Render `DaySummaryScreen` with a `Ready` state holding two sections. Assert: (a) both section labels and every task label are displayed, in the state's own order; (b) a multi-occurrence task shows its recorded count against its limit; (c) **the screen exposes nothing tappable that could change anything** — assert no node with a click action carries any of the labels "Complete", "Undo", "Add", "Delete", "Edit"; (d) rendering the `NoRecord` state displays a plain statement and no error styling. Because `DaySummaryScreen` takes no `onEvent` parameter at all, (c) is a check that the composable did not grow one. Run and confirm it fails.
- [X] T057 [US3] CREATE `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryScreen.kt` — a stateless `@Composable` taking only `state: DaySummaryUiState`. Render sections and tasks with each task's recorded count and points. Arabic task labels render in the Arabic face with their own direction and must not reflow the surrounding layout (FR-021) — reuse `app/src/main/java/com/giraffe/mizanapp/today/MizanTypography.kt`. Render `NoRecord` as a plain statement that nothing was recorded, never as an error. Run `./gradlew :app:connectedDebugAndroidTest` — T056a must pass.
- [X] T058 [US3] MODIFY `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` to handle `Destination.DaySummary(date)`: `WeekEvent.OpenDay` navigates to it, and back returns to `Week`. Only cells whose `isOpenable` is true may be tapped — `OUTSIDE_RECORD` and `NOT_YET_ELAPSED` cells open nothing. Obtain the ViewModel with the date parameter using `koinViewModel<DaySummaryViewModel> { parametersOf(date) }`, importing `org.koin.core.parameter.parametersOf` and `org.koin.androidx.compose.koinViewModel`. Without the `parametersOf` block Koin cannot satisfy the parameterised definition in T059 and will fail at runtime, not at compile time.
- [X] T059 [US3] MODIFY `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` to add `factory { GetDaySummary(get(), get()) }` to `domainModule` and `viewModel { (date: LocalDate) -> DaySummaryViewModel(get(), date) }` to `appModule`. The parenthesised `(date: LocalDate)` is Koin's injected-parameter syntax and must match the `parametersOf(date)` call site added in T058.

**Checkpoint**: A past day can be inspected and cannot be edited.

---

## Phase 6: User Story 4 — Move between weeks (Priority: P3)

**Goal**: Step back to previous weeks and forward again, bounded by the record.

**Independent Test**: With several weeks of seeded records, step back to the earliest and forward to
the current week; each week's figures are stable and movement stops at both ends without an error.

### Tests for User Story 4 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T060 [P] [US4] CREATE `app/src/test/java/com/giraffe/mizanapp/week/WeekNavigationTest.kt`. Assert: `PreviousWeek` moves to the preceding Saturday-to-Friday week with its own figures; at the week containing the record start, `canGoPrevious` is false and `PreviousWeek` changes nothing and produces no error; at the current week, `canGoNext` is false and `NextWeek` changes nothing; showing the same week twice yields identical figures; a fresh ViewModel always opens on the current week even after navigating away (FR-019); advancing the fake clock past local midnight into a new week and re-invoking the refresh moves the sheet to the new week (FR-020).

### Implementation for User Story 4

- [X] T061 [US4] MODIFY `app/src/main/java/com/giraffe/mizanapp/week/WeekViewModel.kt` to handle `WeekEvent.PreviousWeek` and `WeekEvent.NextWeek` by moving the viewed week seven days and reloading. Compute `canGoPrevious` as "the viewed week's start is after the week containing the record start" and `canGoNext` as "the viewed week's start is before the week containing today". Derive both with `WeekBoundary.weekContaining(...)` — **never by computing a week start inline** (FR-001, Principle VII).
- [X] T061a [US4] CREATE the failing UI test `app/src/androidTest/java/com/giraffe/mizanapp/week/WeekNavigationScreenTest.kt` using `createComposeRule()`. Assert: (a) with `canGoPrevious = true`, tapping the previous affordance emits exactly one `WeekEvent.PreviousWeek`; (b) with `canGoNext = true`, tapping next emits exactly one `WeekEvent.NextWeek`; (c) with `canGoPrevious = false`, the previous affordance is disabled or absent, tapping it emits nothing, and **no message or explanation is displayed** — assert no text containing "earliest", "cannot" or "no more" exists (FR-018); (d) the same for `canGoNext = false`. Run and confirm it fails.
- [X] T062 [US4] MODIFY `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt` to add previous/next affordances driven by `canGoPrevious` and `canGoNext`. At a bound the affordance is simply unavailable — no error, no message, no explanation (FR-018). Run `./gradlew :app:connectedDebugAndroidTest` — T061a must pass.
- [X] T063 [US4] MODIFY `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` so the week route re-checks the current date on `RESUMED`, mirroring the existing `LaunchedEffect` + `repeatOnLifecycle` block used by `TodayRoute`. Crossing local midnight into a new week must move the sheet to the new week.
- [X] T064 [US4] Run `./gradlew :app:testDebugUnitTest` — T060 must pass.

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T065 [P] Verify the performance budget from SC-013 with an instrumented measurement in `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/WeekPerformanceTest.kt`: a week requiring all seven days to be backfilled returns final figures within 300 ms, and so does a no-backfill week against a store seeded with a year of plans and completions. If the budget is missed, **report it as a finding about `002`'s storage design** — do not loosen the number and do not add a cache. `docs/PLAN.md` defers caching until a measurement demands it, and this is that measurement.
- [X] T066 [P] Assert SC-013a in the same file or in `app/src/test/java/com/giraffe/mizanapp/week/WeekViewModelTest.kt`: capture the rendered week state at first emission of `Status.Ready` and again after all writes have settled, and assert the two are identical — no cell changes under the user as a result of backfill completing.
- [X] T066a CREATE `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ReadOnlySheetTest.kt`, extending `DbTestBase` — the empirical proof of SC-010 and FR-024, and the behavioural check that Principle VI holds. Seed a week of mixed activity. Capture a full snapshot of stored state: every `day_plans` row, every `planned_tasks` row, and every `completions` row **including tombstones**, via `countAllRows` and direct DAO reads. Then exercise, through the use cases the two screens call, everything those screens can do: `GetWeekSummary` for the current week and for a previous week, `GetDaySummary` for each of the seven dates, and a second `GetWeekSummary` on the same weeks. Capture the snapshot again and assert the two are **identical** — same row count, same ids, same `pointsAwarded`, same `availablePoints`, same `origin`, same `reversedAt`. The one permitted difference is plans newly created by backfill on first view; run the exercise twice and compare the second and third snapshots so backfill is already settled and the comparison is exact.
- [X] T066b Assert FR-025 — that this increment does not widen the single day-writability rule. Confirm `domain/src/main/kotlin/com/giraffe/mizanapp/domain/policy/DayWritePolicy.kt` is untouched by this branch with `git diff origin/develop-v1 -- domain/src/main/kotlin/com/giraffe/mizanapp/domain/policy/DayWritePolicy.kt`, which must produce no output. Then confirm the only callers of `DayWritePolicy` are still `RoomCompletionRepository` and its tests with `grep -rn "DayWritePolicy" --include=*.kt .` — neither `GetWeekSummary`, `GetDaySummary`, `WeekViewModel` nor `DaySummaryViewModel` may appear. Record both results in the PR description.
- [X] T067 Perform the Principle IX audit required by SC-011 over `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt` and `app/src/main/java/com/giraffe/mizanapp/daysummary/DaySummaryScreen.kt`, using the state enumeration in [contracts/ui-state.md](./contracts/ui-state.md). Walk every state the two new screens can show — five `DayCellState` values, four `WeekUiState.Status` values, three `DaySummaryUiState.Status` values — and check each rendered string, colour and icon for a negative quantity, a penalty, red, a cross, or language implying fault. Pay particular attention to the three states that read alike but mean different things: outside the record, not yet elapsed, and nothing recorded. None is a failure, and each must be visually distinct from the others (FR-016, FR-017a). Record the result in the PR description.
- [X] T068 Run the full validation from [quickstart.md](./quickstart.md): `./gradlew test connectedAndroidTest`, then the manual smoke check on a fresh install in airplane mode.
- [X] T069 Confirm the release-gate items: `data/schemas/2.json` is committed; `MIGRATION_1_2` is purely additive; `fallbackToDestructiveMigration` appears nowhere in the repository (`grep -rn "fallbackToDestructiveMigration" --include=*.kt .` must return nothing).
- [X] T070 MODIFY `docs/GLOSSARY.md` to add the terms this increment introduces: Week Key, Day Summary, Record Start, and Plan Origin. Follow the file's existing style — one meaning per term, no technology in any definition, and an explicit contrast where two terms are easy to confuse.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies
- **Phase 2 (Foundational)**: depends on Phase 1 — **blocks every user story**
- **Phase 3 (US1)**: depends on Phase 2
- **Phase 4 (US2)**: depends on Phase 3 — `GetWeekSummary` must exist before backfill is inserted into it
- **Phase 5 (US3)**: depends on Phase 2 only; independent of US2
- **Phase 6 (US4)**: depends on Phase 3 (needs `WeekViewModel`)
- **Phase 7 (Polish)**: depends on all desired stories

### Within Phase 2 — order matters

2a (T003–T005) is independent of 2b and 2c and can be done first or in parallel.
2b (T006–T018) is strictly sequential; the schema change touches several files in a chain.
2c (T019–T020) is independent of 2a and 2b.
2d (T021–T029) depends on 2b, because `plansBetween` returns plans that now carry `origin`.

### The one cross-story dependency worth naming

US2 modifies the `GetWeekSummary` created in US1. That is deliberate: US1 proves the aggregate is
right against days that exist, and US2 then makes days exist. Splitting them the other way would
mean writing backfill with nothing to check it against.

### Parallel opportunities

- T003 and T019 and T021 can be written together — three different test files, no shared code
- T030, T031, T032 — all three US1 test files
- T033, T034 — two independent domain files
- T040a is independent of T033–T039 — it tests the composable against a hand-built `WeekUiState`
- T044, T045 — one JVM test and one instrumented test
- T051, T052, T056a — three independent US3 test files
- T065, T066 — two independent measurements

---

## Implementation Strategy

### MVP scope

**US1 + US2 together.** They are both P1 and the spec says they are inseparable — a sheet that omits
skipped days reports a week total the user did not earn. Shipping US1 alone would put a wrong number
in front of the user on day one. So the MVP is Phases 1, 2, 3 and 4.

### Incremental delivery

1. Phases 1–2 → foundation ready, nothing user-visible, everything still green
2. Phase 3 → the sheet renders for days that were opened
3. Phase 4 → skipped days appear; **stop and validate — this is the shippable increment**
4. Phase 5 → day drill-in
5. Phase 6 → week navigation
6. Phase 7 → audit, measure, document

### Committing

Commit after each task or small logical group. **The test task's commit must precede the
implementation task's commit** — merges to `develop-v1` are squash-only, so the pull request is the
only place that ordering is visible, and it is checked before merge. A PR whose first commit is
production code has already failed the gate.

---

## Notes

- Lettered ids (`T040a`, `T048a`, `T056a`, `T061a`, `T066a`, `T066b`) are tasks inserted after the
  first draft. Execute them in the position they appear — the letter marks an insertion, not an
  optional extra
- `[P]` means a different file with no dependency on an incomplete task
- Verify each test fails, and fails for the right reason, before writing the code that satisfies it
- Never weaken an existing `002` assertion to make a new change compile — if an old test fails, the
  new code is wrong
- If a task's instruction contradicts [spec.md](./spec.md) or the constitution, stop and report it
  rather than choosing one
