# Tasks: Today Screen — Local Task Engine

**Feature**: `002-today-task-engine` | **Branch**: `spec/002-today-task-engine` | **Date**: 2026-08-09

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

---

## READ THIS FIRST — Rules for whoever implements this

These are not suggestions. Breaking any one of them fails the increment.

1. **Do the tasks in numeric order.** Do not skip ahead. Do not batch several tasks then test once.
2. **Red before green.** A task marked `[TEST]` must be written **and observed failing** before the
   task after it. **A compile failure counts as a valid red** — a test referring to a class that
   does not exist yet is a legitimate failing test.
3. **After every task**, run the command printed in that task and confirm the stated result. If it
   differs, stop and report the task number, the command, and the actual output. Do not continue.
4. **Never put Android on `:domain`.** No `import android.*`, no `import androidx.*`, no Room, no
   Compose, no Koin. `:domain` is a plain Kotlin JVM module — those imports will not compile there,
   and that is the point. If you feel you need one, you have misread a task.
5. **Never read the clock directly.** `LocalDate.now()`, `Instant.now()`, and
   `System.currentTimeMillis()` are banned everywhere except inside `SystemTimeProvider`. Use the
   injected `TimeProvider`.
6. **Never change the numbers.** 69, 74, 76, 500, and 18 for Adhkar are fixed. If a test fails on
   arithmetic, the code is wrong, never the expected value.
7. **Do not invent content.** Every file's content is given below or derived by a stated rule. If
   something is missing, stop and report it.
8. **Do not reformat files you were not asked to change.**
9. **Kotlin file locations differ per module.** `:app` uses `src/test/java/` and `src/main/java/`
   (existing convention). `:domain` and `:data` are new and use `src/main/kotlin/` and
   `src/test/kotlin/`. Follow exactly what each task says.
10. **Test fixtures never ship.** Only `valid-catalogue.json` goes in `src/main/resources`. Every
    deliberately-broken fixture lives in `src/test/resources` and must not reach the APK.

### Commands you will use

```bash
./gradlew :domain:test                    # JVM tests, fast
./gradlew :app:testDebugUnitTest          # JVM tests
./gradlew :data:connectedDebugAndroidTest # needs a device or emulator
./gradlew assembleDebug                   # whole build
```

On Windows use `.\gradlew.bat`.

### Two facts you must not get wrong

**Available points formula** — memorise it:

```text
availablePoints(date) = sum over tasks whose schedule matches date of
                        (points x maxOccurrencesPerDay)
```

**Adhkar is ONE task with a limit of 9**, not nine tasks. `2 x 9 = 18`. The catalogue is corrected
in Phase 2. If you see nine adhkar rows anywhere after T019, something went wrong.

### A note on KSP

Room needs the KSP annotation processor. This does **not** violate the constitution's ban — that
ban is on *KSP-based dependency injection* (Hilt), and Koin needs no processor. Room's use of KSP is
persistence, not DI.

### A note on the Hijri date

It is **computed on the device** from the civil date. There is no network anywhere in this feature.
If you find yourself adding Retrofit, an API client, or a DTO, stop — you have misread a task.

---

## Phase 1: Setup — modules and build files

- [ ] T001 In `gradle/libs.versions.toml` under `[versions]` add: `room = "2.8.1"`, `koin = "4.1.0"`, `ksp = "2.2.10-2.0.2"`, `desugar = "2.1.5"`, `coroutines = "1.10.2"`. Do not change existing lines.

- [ ] T002 In `gradle/libs.versions.toml` under `[libraries]` add: `room-runtime`, `room-ktx`, `room-compiler` (group `androidx.room`, names `room-runtime` / `room-ktx` / `room-compiler`, `version.ref = "room"`); `koin-android` and `koin-androidx-compose` (group `io.insert-koin`, `version.ref = "koin"`); `kotlinx-coroutines-core` and `kotlinx-coroutines-test` (group `org.jetbrains.kotlinx`, `version.ref = "coroutines"`); `desugar-jdk-libs` (group `com.android.tools`, name `desugar_jdk_libs`, `version.ref = "desugar"`); `androidx-room-testing` (group `androidx.room`, name `room-testing`, `version.ref = "room"`).

- [ ] T003 In `gradle/libs.versions.toml` under `[plugins]` add: `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }`, `android-library = { id = "com.android.library", version.ref = "agp" }`, `kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }`.

- [ ] T004 In the root `build.gradle.kts`, add to the existing `plugins { }` block: `alias(libs.plugins.ksp) apply false`, `alias(libs.plugins.android.library) apply false`, `alias(libs.plugins.kotlin.jvm) apply false`. Keep the three existing lines.

- [ ] T005 Replace `settings.gradle.kts`'s last line `include(":app")` with three lines: `include(":app")`, `include(":data")`, `include(":domain")`. Change nothing else in the file.

- [ ] T006 Create `domain/build.gradle.kts`. It must apply **only** `alias(libs.plugins.kotlin.jvm)` and `alias(libs.plugins.kotlin.serialization)`. Add `kotlin { jvmToolchain(11) }`. Dependencies: `implementation(libs.kotlinx.serialization.json)`, `implementation(libs.kotlinx.coroutines.core)`, `testImplementation(libs.junit)`. **Do not apply any Android plugin here.** This module must never see the Android SDK.

- [ ] T007 Create `data/build.gradle.kts` applying `alias(libs.plugins.android.library)` and `alias(libs.plugins.ksp)`. Set `namespace = "com.giraffe.mizanapp.data"`, `compileSdk { version = release(37) }`, `defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`, `compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; isCoreLibraryDesugaringEnabled = true }`. Add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Dependencies: `api(project(":domain"))`, `implementation(libs.room.runtime)`, `implementation(libs.room.ktx)`, `ksp(libs.room.compiler)`, `coreLibraryDesugaring(libs.desugar.jdk.libs)`, `androidTestImplementation(libs.androidx.junit)`, `androidTestImplementation(libs.androidx.room.testing)`, `androidTestImplementation(libs.kotlinx.coroutines.test)`. **Do not add Koin here** — all DI modules are declared in `:app`, so `:data` has no use for it.

- [ ] T008 In `app/build.gradle.kts` add to `compileOptions`: `isCoreLibraryDesugaringEnabled = true`. Add to `dependencies`: `implementation(project(":data"))`, `implementation(project(":domain"))`, `implementation(libs.koin.android)`, `implementation(libs.koin.androidx.compose)`, `coreLibraryDesugaring(libs.desugar.jdk.libs)`, `testImplementation(libs.kotlinx.coroutines.test)`. **Desugaring is mandatory** — the domain model uses `java.time` and `minSdk` is 24, which would crash on API 24 and 25 without it.

- [ ] T009 Create the source directories: `domain/src/main/kotlin/com/giraffe/mizanapp/domain/`, `domain/src/main/resources/catalogue/`, `domain/src/test/kotlin/com/giraffe/mizanapp/domain/`, `domain/src/test/resources/catalogue/bad/`, `data/src/main/kotlin/com/giraffe/mizanapp/data/`, `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/`.

- [ ] T010 Run `./gradlew assembleDebug`. **Expected**: BUILD SUCCESSFUL. Three modules configure. If Gradle cannot resolve a dependency, fix T001–T003 before continuing.

**Checkpoint**: three modules exist and build. No feature code yet.

---

## Phase 2: Foundational — move `001`, correct the catalogue, inject the clock

Everything in Phase 3 onward depends on this. Nothing here is optional.

### 2a — Move the catalogue model into `:domain`

- [ ] T011 Move these six files from `app/src/test/java/com/giraffe/mizanapp/catalogue/model/` to `domain/src/main/kotlin/com/giraffe/mizanapp/domain/catalogue/`: `Catalogue.kt`, `CatalogueVersion.kt`, `Section.kt`, `TaskDefinition.kt`, `TaskVersion.kt`, `ScheduleRule.kt`. Change each file's `package` line to `com.giraffe.mizanapp.domain.catalogue`. Change no other code.

- [ ] T012 Move `CatalogueDefect.kt`, `CatalogueValidator.kt` and `CatalogueJson.kt` from `app/src/test/java/com/giraffe/mizanapp/catalogue/` to `domain/src/main/kotlin/com/giraffe/mizanapp/domain/catalogue/`, changing the `package` line to `com.giraffe.mizanapp.domain.catalogue` and fixing imports of the model classes. Change no logic.

- [ ] T013 Move the test files `CatalogueJsonTest.kt`, `CatalogueValidatorTest.kt`, `CatalogueArithmeticTest.kt`, `CatalogueMutationTest.kt` and `Fixtures.kt` to `domain/src/test/kotlin/com/giraffe/mizanapp/domain/catalogue/`, updating package and imports. Change no assertions.

- [ ] T014 Move **only** `app/src/test/resources/catalogue/good/valid-catalogue.json` to `domain/src/main/resources/catalogue/valid-catalogue.json` — note it moves up one level, out of the `good/` folder. This single file is the shipped seed.

- [ ] T015 Move the **entire** `app/src/test/resources/catalogue/bad/` directory to `domain/src/test/resources/catalogue/bad/`. All 18 defect fixtures live on the test classpath only. **They must never be packaged into the APK.** Then update `Fixtures.kt`: `GOOD` becomes `"/catalogue/valid-catalogue.json"` and `bad(name)` reads `"/catalogue/bad/$name"`. Both resolve from the test classpath, which sees `main` and `test` resources both, so nothing breaks.

- [ ] T016 Delete `app/src/test/java/com/giraffe/mizanapp/catalogue/DomainPurityTest.kt`. It scanned source text for `import android.`, which the module boundary now enforces at compile time. T017 replaces it.

- [ ] T017 [TEST] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/ModuleBoundaryTest.kt`. **Gradle runs JVM unit tests with the module directory as the working directory**, so read `File("build.gradle.kts")` — not a path with `../`. Assert the file exists, that its text contains `kotlin.jvm`, and that it does **not** contain `com.android.library`, `com.android.application`, or `androidx.room`. Run `./gradlew :domain:test`. **Expected: this test passes and the moved `001` suite passes.**

- [ ] T018 Run `./gradlew :app:testDebugUnitTest :domain:test`. **Expected**: BUILD SUCCESSFUL. `:app` now has only `ExampleUnitTest`; `:domain` has the moved `001` tests. If `:app` fails to compile, a stale import to the old catalogue package remains — remove it.

### 2b — Correct the catalogue (Adhkar)

- [ ] T019 Edit `domain/src/main/resources/catalogue/valid-catalogue.json`. In `tasks`, delete the nine entries `adhkar-1` … `adhkar-9` and replace them with exactly one: `{ "slug": "adhkar", "sectionId": "adhkar", "displayPosition": 1, "label": "Adhkar" }`. In `taskVersions`, delete the nine matching entries and replace them with exactly one: `{ "taskSlug": "adhkar", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 9, "scheduleRule": { "type": "everyDay" } }`. Totals are unchanged: `2 x 9 = 18`, base day still 69.

- [ ] T020 In `domain/src/test/kotlin/com/giraffe/mizanapp/domain/catalogue/CatalogueJsonTest.kt`, change the two count assertions from `40` tasks and `40` taskVersions to **32** and **32**. Change nothing else in that file.

- [ ] T021 Fix `domain/src/test/resources/catalogue/bad/duplicate-position-in-section.json`, which previously collided `adhkar-2` with `adhkar-1`. Set task `fajr-2`'s `displayPosition` to `1` instead, colliding with `fajr-1`. Then in `CatalogueValidatorTest.kt`, in the test named `rule 6 - duplicate display position within a section is rejected`, change the three expected values from `("adhkar", 1, listOf("adhkar-1", "adhkar-2"))` to `("fajr", 1, listOf("fajr-1", "fajr-2"))`.

- [ ] T022 Fix `domain/src/test/resources/catalogue/bad/wrong-section-composition.json`, which previously deleted `adhkar-9`. Instead set the single `adhkar` task version's `maxOccurrencesPerDay` from `9` to `8`, making the section total 16. In `CatalogueValidatorTest.kt`, the test `rule 14 - wrong section composition is rejected` already expects `expected = 18` and `actual = 16`, so **its assertions need no change** — confirm they still pass.

- [ ] T023 Run `./gradlew :domain:test`. **Expected**: all tests pass. **The `001` validation contract itself must not be edited.** If a rule had to be weakened to accept the corrected catalogue, stop and report — that means the correction or the contract is wrong, and the arithmetic is never the thing to change.

### 2c — Time

- [ ] T024 [TEST] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/DayBoundaryTest.kt` asserting: given an instant and a zone, `DayBoundary.dateAt(instant, zone)` returns the local civil date; one millisecond before local midnight belongs to the earlier date and one millisecond after to the later; the result changes when the zone changes. Run `./gradlew :domain:test`. **Expected: COMPILE FAILURE** — `DayBoundary` does not exist. Observe it.

- [ ] T025 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/TimeProvider.kt`: `interface TimeProvider { fun now(): Instant; fun today(): LocalDate; fun zone(): ZoneId }`. In the same package create `DayBoundary.kt` with `object DayBoundary { fun dateAt(instant: Instant, zone: ZoneId): LocalDate }` implemented as `instant.atZone(zone).toLocalDate()`. **This is the only place the day boundary rule may exist** (FR-030). Run `./gradlew :domain:test`. **Expected: T024 passes.**

- [ ] T026 [P] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/FakeTimeProvider.kt`: a `TimeProvider` whose instant and zone are mutable `var`s, plus `fun advanceBy(duration: Duration)` and `fun setDate(date: LocalDate)`. Every domain test that needs time uses this. No test may read the real clock.

- [ ] T027 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/time/SystemTimeProvider.kt` implementing `TimeProvider` with `Instant.now()`, `ZoneId.systemDefault()`, and `today()` delegating to `DayBoundary.dateAt(now(), zone())`. **This is the only file in the entire project permitted to call `Instant.now()` or `ZoneId.systemDefault()`.**

### 2d — Hijri label (computed locally, no network)

- [ ] T028 [TEST] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/HijriLabelTest.kt` asserting `HijriLabel.forDate(LocalDate.of(2026, 1, 1))` returns a non-blank string, that the same input always returns the identical string, and that two consecutive civil dates never produce the same label. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T029 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/HijriLabel.kt` with `object HijriLabel { fun forDate(date: LocalDate): String }` using `java.time.chrono.HijrahChronology.INSTANCE.date(date)` and formatting as `"d MMMM yyyy"`. **No network, no I/O, no clock.** Run tests. **Expected: T028 passes.**

**Checkpoint**: `:domain` holds the catalogue, time, and Hijri label. Nothing Android-specific yet.

---

## Phase 3: User Story 1 — Record today's practice (Priority: P1) 🎯 MVP

**Goal**: open the app, see today's applicable tasks by section, complete and undo them including
multiple occurrences, and see earned against available.

**Independent test**: fresh install in airplane mode on a known weekday shows the right task set and
the right available total; completing and undoing move the earned total by the right amount.

### 3a — Domain: applicability

- [ ] T030 [TEST] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/ResolveApplicableTasksTest.kt`. Load the good catalogue via the existing `Fixtures` helper. Assert: on Saturday the result excludes `fast-voluntary` and all `friday-*`; on Monday it includes `fast-voluntary` but no `friday-*`; on Friday it includes all seven `friday-*` but not `fast-voluntary`; the available totals of the resolved sets are 69, 74 and 76 respectively. Run `./gradlew :domain:test`. **Expected: COMPILE FAILURE.**

- [ ] T031 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/ResolveApplicableTasks.kt` with `fun resolveApplicableTasks(catalogue: Catalogue, version: Int, date: LocalDate): List<TaskVersion>` filtering `taskVersions` by `catalogueVersion == version` and `scheduleRule.matches(date.dayOfWeek)`. Pure. Run tests. **Expected: T030 passes.**

### 3b — Domain: the day plan

- [ ] T032 [P] [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/DayPlan.kt` and `PlannedTask.kt` as data classes with exactly the fields listed in [data-model.md](./data-model.md) Part 1. `DayPlan` has `id`, `date`, `catalogueVersion`, `hijriLabel: String` (**non-null**), `availablePoints`, `plannedTasks`. `PlannedTask` has `id`, `dayPlanId`, `taskSlug`, `sectionId`, `sectionLabel`, `sectionOrder`, `displayPosition`, `label`, `points`, `maxOccurrencesPerDay`. No logic in either.

- [ ] T033 [TEST] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/BuildDayPlanTest.kt`. Assert: a plan built for a Saturday has `availablePoints == 69`; for a Monday 74; for a Friday 76; every planned task snapshots its label, points and limit from the catalogue; the adhkar planned task has `maxOccurrencesPerDay == 9` and contributes 18; the plan carries a non-blank `hijriLabel`; the plan's id and each planned task's id are non-blank and unique. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T034 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/BuildDayPlan.kt` with `fun buildDayPlan(catalogue: Catalogue, version: Int, date: LocalDate, newId: () -> String): DayPlan`. It resolves applicable tasks, snapshots section label and order from the catalogue, computes `availablePoints` as the sum of `points * maxOccurrencesPerDay`, and sets `hijriLabel = HijriLabel.forDate(date)`. Pure — no clock, no I/O, ids supplied by the `newId` lambda so tests are deterministic. Run tests. **Expected: T033 passes.**

### 3c — Domain: completions, occurrences and scoring

- [ ] T035 [P] [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/Completion.kt` with fields `id`, `dayPlanId`, `taskSlug`, `creditedDate`, `pointsAwarded`, `recordedAt: Instant`, `reversedAt: Instant?`. Add `val isLive: Boolean get() = reversedAt == null`.

- [ ] T036 [TEST] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/OccurrenceTest.kt`. Assert with a list of completions: `liveCount` counts only rows whose `reversedAt` is null; a task at its limit reports `canRecord == false`; **after reversing one, `canRecord` is true again**; reversing does not change earlier live rows. Include the SC-012 loop — record to the limit, reverse, record again, ten times — and assert the live count and earned total each time equal the never-reversed case. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T037 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/Occurrences.kt` with `fun liveCount(completions: List<Completion>, taskSlug: String): Int` and `fun canRecord(completions: List<Completion>, task: PlannedTask): Boolean`. Both filter on `isLive`. **This filter is the single most important line in the increment** — without it one mistaken tap locks a task at its limit forever. Run tests. **Expected: T036 passes.**

- [ ] T038 [TEST] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/ScoreDayTest.kt`. Assert: no completions gives `earned == 0` and `available == 69` on a Saturday; all tasks completed to their limits gives `earned == available`; reversed completions contribute nothing; `earned` is never negative and never exceeds `available`; `fraction` is 0 when available is 0. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T039 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/DailyScore.kt` (data class `earned`, `available`, with `fraction` a computed property) and `ScoreDay.kt` with `fun scoreDay(plan: DayPlan, completions: List<Completion>): DailyScore` summing `pointsAwarded` over live completions. Run tests. **Expected: T038 passes.**

- [ ] T040 [TEST] [US1] Add to `ScoreDayTest.kt` the SC-003 invariant test: build a Saturday plan, then run a **seeded** sequence (use `Random(42)` so it reproduces exactly) of **20 mixed operations** — each either a record on a randomly chosen task that is under its limit, or an undo on a randomly chosen task that has a live completion. After **every** operation assert `scoreDay(...).earned` equals the sum of `pointsAwarded` over the live completions, and that `earned` never exceeds `available`. Run tests.

### 3d — Domain: write policy and repository interfaces

- [ ] T041 [TEST] [US1] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/policy/DayWritePolicyTest.kt` asserting today is writable and yesterday and tomorrow are not, using `FakeTimeProvider`. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T042 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/policy/DayWritePolicy.kt` with `class DayWritePolicy(private val time: TimeProvider) { fun isWritable(date: LocalDate): Boolean = date == time.today() }`. **Phase 5 widens this one file and nothing else.** Run tests. **Expected: T041 passes.**

- [ ] T043 [P] [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CatalogueRepository.kt`, `DayPlanRepository.kt` and `CompletionRepository.kt` with **exactly** the interfaces and sealed result types given in [contracts/repositories.md](./contracts/repositories.md). Note `DayPlanRepository` has **no** method to set or change a Hijri label, and both `RecordOutcome` and `UndoOutcome` include a `NotWritable` case. Interfaces only — no implementations in `:domain`.

- [ ] T044 [US1] Run `./gradlew :domain:test`. **Expected**: BUILD SUCCESSFUL, all domain tests green. Domain is complete for US1.

### 3e — Data: Room entities and DAOs

- [ ] T045 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/CatalogueEntities.kt` with `@Entity` classes `SectionEntity` (PK `id`), `TaskDefinitionEntity` (PK `slug`), `CatalogueVersionEntity` (PK `version`), `TaskVersionEntity` (PK `id`, columns `taskSlug`, `catalogueVersion`, `points`, `maxOccurrencesPerDay`, `scheduleType`, `scheduleDays`, plus `updatedAt`, `deletedAt`, `userId`). Store the schedule rule as a discriminator string plus a comma-separated day list — a sealed type does not map to a column.

- [ ] T046 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/DayEntities.kt` with `DayPlanEntity` (PK `id` TEXT, unique index on `date`, `hijriLabel` **non-null TEXT**, plus `updatedAt`, `deletedAt`, `userId`), `PlannedTaskEntity` (PK `id`, index on `dayPlanId`), and `CompletionEntity` (PK `id`, index on `creditedDate` and on `dayPlanId, taskSlug`, columns `pointsAwarded`, `recordedAt`, `reversedAt`, plus `updatedAt`, `deletedAt`, `userId`). Dates stored as ISO strings, instants as epoch millis.

- [ ] T047 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Converters.kt` with Room `@TypeConverter`s for `LocalDate` ↔ `String` and `Instant` ↔ `Long`. Null-safe both ways.

- [ ] T048 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CatalogueDao.kt` with insert methods (`OnConflictStrategy.IGNORE`) for the four catalogue tables, a `countVersions()`, and `versionEffectiveOn(date: String): Int?` returning the greatest `version` whose `effectiveFrom <= date`, or null.

- [ ] T049 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/DayPlanDao.kt` with `insertPlan`, `insertPlannedTasks`, `planByDate(date: String): DayPlanWithTasks?`, and `observePlanByDate(date: String): Flow<DayPlanWithTasks?>`. **There must be no update method of any kind for day plans** — not for the Hijri label, not for anything. The DAO must offer no way to express the forbidden operation.

- [ ] T050 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CompletionDao.kt` with `insert(completion)`, `observeLiveByDate(date: String): Flow<List<CompletionEntity>>` filtered `WHERE reversedAt IS NULL`, `liveCount(date: String, slug: String): Int` likewise filtered, and `reverseLatest(date: String, slug: String, at: Long): Int` setting `reversedAt` on the single most recent live row. **Every read filters `reversedAt IS NULL`.**

- [ ] T051 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`: `@Database(entities = [...7 entities...], version = 1, exportSchema = true)` with `@TypeConverters(Converters::class)` and abstract accessors for the three DAOs.

- [ ] T052 [US1] Run `./gradlew :data:assembleDebug`. **Expected**: BUILD SUCCESSFUL, and `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/1.json` now exists. **Commit that schema file** — the constitution requires exported schemas.

### 3f — Data: mappers, seeder, repositories

- [ ] T053 [TEST] [US1] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/mapper/MapperTest.kt` asserting round trips: domain → entity → domain returns an equal object for `DayPlan`, `PlannedTask`, `Completion`, and `TaskVersion` including both schedule rule shapes. Mappers are **not** exempt from test-first. Run `./gradlew :data:connectedDebugAndroidTest`. **Expected: COMPILE FAILURE.**

- [ ] T054 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/mapper/Mappers.kt` with pure extension functions both directions for the four types. Schedule rule maps to `scheduleType` plus `scheduleDays`; an unknown discriminator throws, because it means corrupt storage rather than an expected state. Run tests. **Expected: T053 passes.**

- [ ] T055 [TEST] [US1] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/seed/CatalogueSeederTest.kt` asserting: seeding an empty database returns `Seeded` with 32 tasks; seeding again returns `AlreadyPresent` and leaves every row count identical; seeding a catalogue with a known defect returns `Failed` and writes **nothing at all**. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T056 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/seed/CatalogueSeeder.kt`. It reads `/catalogue/valid-catalogue.json` from the classpath via `CatalogueSeeder::class.java.getResourceAsStream`, parses it with the `001` `parseCatalogue`, validates it with `CatalogueValidator`, and inserts only if validation returns an empty list and the database has no versions. All inserts run in one Room transaction so a failure writes nothing. Run tests. **Expected: T055 passes.**

- [ ] T057 [TEST] [US1] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/repository/DayPlanRepositoryTest.kt` asserting: `ensurePlanFor` on an empty database returns `Created`; calling it again returns `AlreadyExists` with the identical plan; the stored `availablePoints` equals the expected weekday total; the created plan carries a non-blank `hijriLabel`. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T058 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomDayPlanRepository.kt` implementing `DayPlanRepository`. `ensurePlanFor` reads the existing plan first and returns it untouched if present; otherwise it resolves the catalogue version for the date, calls the domain's `buildDayPlan`, and inserts plan and planned tasks in one transaction. Run tests. **Expected: T057 passes.**

- [ ] T059 [TEST] [US1] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/repository/CompletionRepositoryTest.kt` asserting: `record` returns `Recorded` and stores the planned points; recording past the limit returns `AtLimit` and writes nothing; `undoLast` returns `Reversed` and sets `reversedAt` without deleting the row; `undoLast` with nothing live returns `NothingToUndo`; **the adhkar task accepts 9, refuses the 10th, and after one undo accepts one more**; a reversed row never appears in `observeLiveByDate`. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T060 [TEST] [US1] Add to the same file the FR-015 enforcement tests: with `FakeTimeProvider` set to a known today, `record` against **yesterday** returns `NotWritable` and writes nothing, and `undoLast` against yesterday likewise returns `NotWritable` and reverses nothing. Assert the completion count for that date is unchanged after both. Run tests. **Expected: FAILS** — the policy is not consulted yet.

- [ ] T061 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCompletionRepository.kt` implementing `CompletionRepository`. It takes a `DayWritePolicy` as a constructor parameter and **consults it first in both `record` and `undoLast`**, returning `NotWritable` and touching no storage when the date is refused. Points come from the stored `PlannedTask`, never from the live catalogue. Run tests. **Expected: T059 and T060 pass.**

- [ ] T062 [P] [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCatalogueRepository.kt` implementing `CatalogueRepository` and delegating seeding to `CatalogueSeeder`.

- [ ] T063 [US1] Run `./gradlew :data:connectedDebugAndroidTest`. **Expected**: all data tests pass. A device or emulator must be connected.

### 3g — App: DI, ViewModel, screen

- [ ] T064 [US1] Create `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` with three Koin modules — `domainModule`, `dataModule`, `appModule` — binding the database, DAOs, the three repositories, `SystemTimeProvider` as `TimeProvider`, `DayWritePolicy`, and `TodayViewModel`. **DI wiring is exempt from test-first** (constitution, Principle I) — this is the only task in the feature claiming that exemption.

- [ ] T065 [US1] Create `app/src/main/java/com/giraffe/mizanapp/MizanApplication.kt` extending `Application`, starting Koin with the three modules, and register it via `android:name=".MizanApplication"` in `app/src/main/AndroidManifest.xml`.

- [ ] T066 [P] [US1] Create `app/src/main/java/com/giraffe/mizanapp/today/TodayUiState.kt` with `TodayUiState`, `SectionUi`, `TaskRowUi` and `TodayEvent` **exactly** as given in [contracts/ui-state.md](./contracts/ui-state.md). Derived values are computed properties, never stored fields.

- [ ] T067 [TEST] [US1] Create `app/src/test/java/com/giraffe/mizanapp/today/TodayViewModelTest.kt` using `FakeTimeProvider` and in-memory fake repositories. Assert one test per transition: initial state is `Loading` then `Ready`; `CompleteTask` raises `earnedPoints` by the task's points; `UndoTask` lowers it by the same; completing past the limit changes nothing; a `NotWritable` outcome leaves every count unchanged; `CatalogueUnavailable` is emitted when seeding fails. Run `./gradlew :app:testDebugUnitTest`. **Expected: COMPILE FAILURE.**

- [ ] T068 [US1] Create `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt` exposing `val state: StateFlow<TodayUiState>` via `MutableStateFlow(...).asStateFlow()` and a single `fun onEvent(event: TodayEvent)`. **No mutable state may be exposed** (constitution). All work in `viewModelScope`. Run tests. **Expected: T067 passes.**

- [ ] T069 [US1] Create `app/src/main/java/com/giraffe/mizanapp/today/TodayScreen.kt` — a Compose screen rendering the date header, the points header as `earned / available`, and the current section's task rows. Each row shows label, points, and for multi-occurrence tasks a `recordedCount/maxOccurrences` counter. Tapping records; a visible undo affordance reverses. **No red, no ✗, no "missed", no negative number, no add/edit/delete affordance anywhere.**

- [ ] T070 [US1] Apply Arabic content rendering to `TodayScreen.kt` (FR-025). Every **task label and section label** is Arabic content and must be rendered in an Arabic-appropriate typeface (IBM Plex Sans Arabic per `CLAUDE.md`'s Design section) with `LocalLayoutDirection provides LayoutDirection.Rtl` scoped to that text only, and `textAlign = TextAlign.Right`, so a mixed Arabic/Latin row never reflows the surrounding layout. Arabic rows use `lineHeight` 1.75em, headings 1.5em. **Interface chrome — buttons, headings, the points header — stays English and left-to-right.** Add the font to `app/src/main/res/font/` and reference it from the theme's typography.

- [ ] T071 [US1] Wire `TodayScreen` into `MainActivity` replacing the template content, collecting state with `collectAsStateWithLifecycle`.

- [ ] T072 [US1] Run `./gradlew assembleDebug` then install and open on a device in **airplane mode**. **Expected**: today's tasks appear with Arabic labels rendered right-to-left, completing and undoing works, the score updates, nothing waits on a network.

**Checkpoint**: User Story 1 complete. This is the MVP.

---

## Phase 4: User Story 2 — Today's record stays true tomorrow (Priority: P2)

- [ ] T073 [TEST] [US2] Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DayPlanImmutabilityTest.kt`. Seed catalogue v1, create a plan for a date, insert a v2 with different points and a different schedule, then re-read the original date. Assert its planned tasks, their points and its `availablePoints` are **identical**, while a plan built for a later date reflects v2. **This is the test the whole storage design exists for.** Run `./gradlew :data:connectedDebugAndroidTest`. **Expected: FAILS** if anything recomputes from the live catalogue.

- [ ] T074 [US2] Fix whatever T073 exposes. If it already passes, record that in the task and move on — the test still has value as a regression guard.

- [ ] T075 [TEST] [US2] Add to the same file: a completion recorded under v1 still reports its original `pointsAwarded` after v2 changes that task's points. Run tests.

- [ ] T076 [TEST] [US2] Add to the same file the SC-005 durability test: write a plan and several completions, **close the database and reopen it** in the same test, then assert every plan field, every completion and the derived score are identical. This covers process death without needing to kill the app. Run tests.

- [ ] T077 [TEST] [US2] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/RolloverTest.kt` using `FakeTimeProvider`: set the clock to 23:59:59 local, advance two seconds, and assert `today()` returns the next date. Assert the previously built plan object is unchanged. Run `./gradlew :domain:test`. **Expected: COMPILE FAILURE or FAIL.**

- [ ] T078 [US2] Add rollover handling to `TodayViewModel`: observe the date from `TimeProvider` and, when it changes, call `ensurePlanFor` the new date and emit a fresh state (FR-023). Run tests.

- [ ] T079 [TEST] [US2] Add a `:data` test asserting `seedIfNeeded` called twice leaves plans and completions untouched (FR-001). Run tests.

- [ ] T080 [US2] Run `./gradlew :domain:test :data:connectedDebugAndroidTest`. **Expected**: all green.

**Checkpoint**: history is provably honest.

---

## Phase 5: User Story 3 — One block at a time (Priority: P3)

- [ ] T081 [TEST] [US3] Create `domain/src/test/kotlin/com/giraffe/mizanapp/domain/day/LandingSectionTest.kt`. Assert: with nothing complete the index is 0; with the first three sections complete it is 3; with everything complete it is 0; **a task at 3 of 9 leaves its section incomplete**. Run tests. **Expected: COMPILE FAILURE.**

- [ ] T082 [US3] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/LandingSection.kt` with `fun landingSectionIndex(sections: List<SectionProgress>): Int`. Pure, derived, never stored (FR-020b). Run tests. **Expected: T081 passes.**

- [ ] T083 [US3] Use it in `TodayViewModel` to set `currentSectionIndex` on load and after rollover. Do **not** persist the position — recompute on every open.

- [ ] T084 [TEST] [US3] Add ViewModel tests: `NextSection` at the last index does nothing; `PreviousSection` at 0 does nothing; neither emits an error; records made in one section survive moving to another. Run `./gradlew :app:testDebugUnitTest`.

- [ ] T085 [US3] Update `TodayScreen` to render one section at a time with forward and back controls, keeping the day's overall totals visible at all times. Preserve the Arabic rendering rules from T070.

- [ ] T086 [US3] Run `./gradlew :app:testDebugUnitTest` and open the app. **Expected**: opening lands on the earliest incomplete section.

---

## Phase 6: User Story 4 — The day carries its Hijri label (Priority: P3)

> The label is already computed and stored by `buildDayPlan` (T034). This phase only surfaces it.

- [ ] T087 [TEST] [US4] Add to `DayPlanRepositoryTest.kt`: a plan created today carries a non-blank `hijriLabel`; reading the plan again after reopening the database returns the **identical** string with no recomputation; two plans created for the same date in different runs carry the same label. Run `./gradlew :data:connectedDebugAndroidTest`.

- [ ] T088 [US4] Show the Hijri label beside the civil date in `TodayScreen`, using the Arabic typography rules from T070 if rendered in Arabic numerals or script.

- [ ] T089 [US4] Verify offline: with the device in airplane mode from first install, both dates appear. **There is no network path in this feature** — if any code here reaches the network, research.md R4 has been misimplemented.

---

## Phase 7: Polish & Cross-Cutting

- [ ] T090 [P] Run the clock audit: `grep -rn "LocalDate.now()\|Instant.now()\|System.currentTimeMillis()\|ZoneId.systemDefault()" domain/src data/src app/src`. **Expected**: hits only in `SystemTimeProvider.kt`. Any other hit violates Principle VII — fix it.

- [ ] T091 [P] Run the purity audit: `grep -rn "^import android\.\|^import androidx\." domain/src/main`. **Expected**: empty. The compiler should already prevent this; confirm the module type was not changed.

- [ ] T092 [P] Confirm no test fixture ships: `ls domain/src/main/resources/catalogue/`. **Expected**: exactly one file, `valid-catalogue.json`. If `bad/` is there, T015 was done wrong and 18 corrupt fixtures are in your APK.

- [ ] T093 Confirm `data/schemas/` contains the exported schema JSON and that it is committed, and that no destructive migration exists: `grep -rn "fallbackToDestructiveMigration" data/src app/src` returns empty.

- [ ] T094 Confirm no authoring affordance reached the UI: `grep -rniE "add task|edit task|delete task|reorder|swipeToDismiss|FloatingActionButton" app/src/main`. **Expected**: empty, or justified in writing.

- [ ] T095 Principle IX pass. Open the screen with nothing completed and read every visible element. **Expected**: no red, no ✗, no "missed", no negative number, no framing of zero as failure. Cross-check the audit list in `CLAUDE.md`'s Design section.

- [ ] T096 Run everything and walk the quickstart: `./gradlew :domain:test :app:testDebugUnitTest :data:connectedDebugAndroidTest`, then follow [quickstart.md](./quickstart.md) end to end including Scenario 2's deliberate `import android.os.Build` break in `:domain` and Scenario 4's undo loop. **Expected**: all green, every stated expectation holds.

---

## Dependencies

```text
Phase 1 Setup (T001-T010)
        |
Phase 2 Foundational (T011-T029)     <- moves 001, corrects Adhkar, injects the clock
        |
Phase 3 US1 (T030-T072)              <- MVP
        |
        +-------------------+-------------------+
        |                   |                   |
   Phase 4 US2         Phase 5 US3         Phase 6 US4
   (T073-T080)         (T081-T086)         (T087-T089)
        |                   |                   |
        +-------------------+-------------------+
                            |
                Phase 7 Polish (T090-T096)
```

- **Phases 1 and 2 block everything.** Nothing in Phase 3 works until the catalogue has moved and
  been corrected.
- **US1 blocks US2, US3 and US4** — each modifies the screen or repositories US1 creates.
- **US2, US3 and US4 are independent of each other** and may be done in any order.
- **Within a phase the order is strict** wherever a `[TEST]` task precedes its implementation.

## Parallel opportunities

- T032, T035, T043 — domain data classes and interfaces, no interdependencies
- T045, T046, T048, T049, T050 — separate entity and DAO files
- T090, T091, T092 — independent audits
- US2, US3 and US4 can be built by different people once US1 is merged

## Implementation strategy

**MVP = Phases 1, 2 and 3 (T001–T072).** That is a working app: open, see today, complete, undo,
see the score, fully offline, Arabic content rendered correctly. US2, US3 and US4 each add one
thing to it.

Suggested delivery: finish Phase 1 and 2 and confirm green — that alone is a meaningful checkpoint,
since it proves the `001` contract survived the module move and the Adhkar correction. Then US1 to
T072. Then the three P2/P3 stories in any order. Then Phase 7.

**If you get stuck**: stop and report the task number, the exact command you ran, and the actual
output. Do not work around a failing test by changing an expected number — 69, 74, 76, 500 and 18
come from `docs/PLAN.md` and are never the thing that is wrong.
