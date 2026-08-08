# Tasks: Domain Foundation — Validation Contract, Glossary, Decisions

**Feature**: `001-domain-foundation` | **Branch**: `spec/001-domain-foundation` | **Date**: 2026-08-08

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md),
[research.md](./research.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

---

## READ THIS FIRST — Rules for whoever implements this

These are not suggestions. Breaking any one of them fails the increment.

1. **Do the tasks in numeric order.** T001, then T002, then T003. Do not skip ahead. Do not batch.
2. **Never create or edit any file under `app/src/main/`.** This feature adds zero production code.
   If you think you need to, you have misread a task.
3. **Never create a new Gradle module.** No `:domain`, no `:data`. Only `:app` exists.
4. **Red before green.** Tasks marked `[TEST]` must be written *and observed failing* before the
   task that makes them pass. Compile failure counts as a valid red — a test referencing a class
   that does not exist yet is a legitimate failing test.
5. **After every task**, run the exact command printed in that task and confirm the stated expected
   result. Do not proceed if it differs.
6. **Do not invent content.** Every file's content is either given literally below or derived by a
   rule stated below. If something seems missing, stop and report it — do not guess.
7. **Do not reformat files you are not asked to change.**
8. **All Kotlin goes in `app/src/test/java/`** (not `kotlin/`). This matches the existing
   `ExampleUnitTest.kt`. Package directories go under `com/giraffe/mizanapp/catalogue/`.
9. **All new Kotlin files must import only `kotlin.*`, `java.time.*`, `kotlinx.serialization.*`,
   and `org.junit.*`.** An `android.` import anywhere in `catalogue/model/` or `CatalogueValidator.kt`
   is a bug that T048 will catch.

### The one command you will run constantly

```bash
./gradlew :app:testDebugUnitTest
```

On Windows use `.\gradlew.bat :app:testDebugUnitTest`.

### Definition of available points (memorise this)

```text
availablePoints(date) = sum over every task whose schedule rule matches date of
                        (points  x  maxOccurrencesPerDay)
```

In the good fixture every `maxOccurrencesPerDay` is `1`, so the totals are just the point sums.

---

## Phase 1: Setup

**Goal**: make the project able to parse JSON in unit tests. No logic yet.

- [ ] T001 Add the serialization plugin and library to the version catalog in `gradle/libs.versions.toml`. Under `[versions]` add `kotlinxSerialization = "1.9.0"`. Under `[libraries]` add `kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }`. Under `[plugins]` add `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`. Do not change any existing line.

- [ ] T002 Register the plugin in the root `build.gradle.kts`. Inside the existing `plugins { }` block, add one line: `alias(libs.plugins.kotlin.serialization) apply false`. Do not remove the two existing lines.

- [ ] T003 Apply the plugin and add the test-only dependency in `app/build.gradle.kts`. In the `plugins { }` block add `alias(libs.plugins.kotlin.serialization)`. In the `dependencies { }` block add exactly one line: `testImplementation(libs.kotlinx.serialization.json)`. **Use `testImplementation`, never `implementation`** — this library must not enter the APK.

- [ ] T004 Verify the build still works. Run `./gradlew :app:testDebugUnitTest`. **Expected**: BUILD SUCCESSFUL, the existing `ExampleUnitTest` passes. If the build fails, fix T001–T003 before continuing.

**Checkpoint**: project compiles, JSON parsing is available to tests only.

---

## Phase 2: Foundational

**Goal**: create the directories. Nothing else is shared between stories.

> User Story 2 (glossary) and User Story 3 (decisions) are pure documentation and share no code
> with User Story 1. There is deliberately no shared model layer here — the model types belong to
> US1 and are created inside its phase.

- [ ] T005 Create these empty directories: `app/src/test/java/com/giraffe/mizanapp/catalogue/`, `app/src/test/java/com/giraffe/mizanapp/catalogue/model/`, `app/src/test/resources/catalogue/good/`, `app/src/test/resources/catalogue/bad/`.

**Checkpoint**: directories exist. No files yet.

---

## Phase 3: User Story 1 — Catalogue validation contract (Priority: P1) 🎯 MVP

**Goal**: an executable contract that accepts a correct catalogue and rejects 16 distinct kinds of
defect.

**Independent test**: `./gradlew :app:testDebugUnitTest` passes with the good fixture returning no
defects and each of the 16 bad fixtures returning its own expected defect.

### 3a — Model types

Data holders only. No logic, no validation inside them.

- [ ] T006 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/ScheduleRule.kt`. Declare `@Serializable sealed interface ScheduleRule`, with `@Serializable @SerialName("everyDay") data object EveryDay : ScheduleRule` and `@Serializable @SerialName("daysOfWeek") data class DaysOfWeek(val days: Set<DayOfWeek>) : ScheduleRule`. Import `java.time.DayOfWeek`. Do **not** create a `DateAnchored` variant — it is reserved, not implemented.

- [ ] T007 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/Section.kt`. `@Serializable data class Section(val id: String, val label: String, val order: Int)`.

- [ ] T008 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/TaskDefinition.kt`. `@Serializable data class TaskDefinition(val slug: String, val sectionId: String, val displayPosition: Int, val label: String)`.

- [ ] T009 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/CatalogueVersion.kt`. `@Serializable data class CatalogueVersion(val version: Int, @Serializable(with = LocalDateSerializer::class) val effectiveFrom: LocalDate)`. In the same file add `object LocalDateSerializer : KSerializer<LocalDate>` that serialises as an ISO-8601 `String` using `LocalDate.parse` and `toString`.

- [ ] T010 [P] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/TaskVersion.kt`. `@Serializable data class TaskVersion(val taskSlug: String, val catalogueVersion: Int, val points: Int, val maxOccurrencesPerDay: Int, val scheduleRule: ScheduleRule)`.

- [ ] T011 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/model/Catalogue.kt`. `@Serializable data class Catalogue(val versions: List<CatalogueVersion>, val sections: List<Section>, val tasks: List<TaskDefinition>, val taskVersions: List<TaskVersion>)`.

- [ ] T012 [US1] Compile check. Run `./gradlew :app:testDebugUnitTest`. **Expected**: BUILD SUCCESSFUL. If serialization annotations fail to resolve, T003 was done wrong.

### 3b — Defect vocabulary

- [ ] T013 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueDefect.kt`. Declare `sealed interface CatalogueDefect` with exactly these 16 data classes/objects, each carrying the listed fields:

  | Variant | Fields |
  |---|---|
  | `DuplicateTaskSlug` | `slug: String`, `count: Int` |
  | `MalformedSlug` | `slug: String` |
  | `NonPositivePoints` | `slug: String`, `points: Int` |
  | `InvalidOccurrenceLimit` | `slug: String`, `value: Int` |
  | `UnknownSection` | `slug: String`, `sectionId: String` |
  | `DuplicateDisplayPosition` | `sectionId: String`, `position: Int`, `slugs: List<String>` |
  | `DuplicateSectionOrder` | `order: Int`, `sectionIds: List<String>` |
  | `UnreachableSchedule` | `slug: String` |
  | `VersionOrderMismatch` | `version: Int`, `effectiveFrom: String` |
  | `DuplicateEffectiveFrom` | `date: String`, `versions: List<Int>` |
  | `WeekdayTotalMismatch` | `dayOfWeek: String`, `expected: Int`, `actual: Int` |
  | `WeekTotalMismatch` | `expected: Int`, `actual: Int` |
  | `SectionCompositionMismatch` | `sectionId: String`, `expected: Int`, `actual: Int` |
  | `UserAuthoringAffordance` | `field: String` |
  | `MalformedCatalogue` | `message: String` |
  | `NoCatalogue` | `path: String` (use a `data class`, not an object) |

### 3c — The good fixture

- [ ] T014 [US1] Create `app/src/test/resources/catalogue/good/valid-catalogue.json` with **exactly** the content below. Do not alter a single number. The arithmetic is load-bearing: 40 tasks, 10 sections, 69/74/76 per day, 500 per week.

```json
{
  "versions": [
    { "version": 1, "effectiveFrom": "2026-01-01" }
  ],
  "sections": [
    { "id": "fajr",       "label": "Fajr",        "order": 1 },
    { "id": "dhuhr",      "label": "Dhuhr",       "order": 2 },
    { "id": "asr",        "label": "Asr",         "order": 3 },
    { "id": "maghrib",    "label": "Maghrib",     "order": 4 },
    { "id": "isha",       "label": "Isha",        "order": 5 },
    { "id": "qiyam-witr", "label": "Qiyam Witr",  "order": 6 },
    { "id": "quran",      "label": "Quran",       "order": 7 },
    { "id": "adhkar",     "label": "Adhkar",      "order": 8 },
    { "id": "fasting",    "label": "Fasting",     "order": 9 },
    { "id": "friday",     "label": "Friday",      "order": 10 }
  ],
  "tasks": [
    { "slug": "fajr-1", "sectionId": "fajr", "displayPosition": 1, "label": "Fajr 1" },
    { "slug": "fajr-2", "sectionId": "fajr", "displayPosition": 2, "label": "Fajr 2" },
    { "slug": "fajr-3", "sectionId": "fajr", "displayPosition": 3, "label": "Fajr 3" },
    { "slug": "fajr-4", "sectionId": "fajr", "displayPosition": 4, "label": "Fajr 4" },
    { "slug": "fajr-5", "sectionId": "fajr", "displayPosition": 5, "label": "Fajr 5" },
    { "slug": "fajr-6", "sectionId": "fajr", "displayPosition": 6, "label": "Fajr 6" },
    { "slug": "dhuhr-1", "sectionId": "dhuhr", "displayPosition": 1, "label": "Dhuhr 1" },
    { "slug": "dhuhr-2", "sectionId": "dhuhr", "displayPosition": 2, "label": "Dhuhr 2" },
    { "slug": "dhuhr-3", "sectionId": "dhuhr", "displayPosition": 3, "label": "Dhuhr 3" },
    { "slug": "dhuhr-4", "sectionId": "dhuhr", "displayPosition": 4, "label": "Dhuhr 4" },
    { "slug": "asr-1", "sectionId": "asr", "displayPosition": 1, "label": "Asr 1" },
    { "slug": "asr-2", "sectionId": "asr", "displayPosition": 2, "label": "Asr 2" },
    { "slug": "asr-3", "sectionId": "asr", "displayPosition": 3, "label": "Asr 3" },
    { "slug": "maghrib-1", "sectionId": "maghrib", "displayPosition": 1, "label": "Maghrib 1" },
    { "slug": "maghrib-2", "sectionId": "maghrib", "displayPosition": 2, "label": "Maghrib 2" },
    { "slug": "maghrib-3", "sectionId": "maghrib", "displayPosition": 3, "label": "Maghrib 3" },
    { "slug": "isha-1", "sectionId": "isha", "displayPosition": 1, "label": "Isha 1" },
    { "slug": "isha-2", "sectionId": "isha", "displayPosition": 2, "label": "Isha 2" },
    { "slug": "isha-3", "sectionId": "isha", "displayPosition": 3, "label": "Isha 3" },
    { "slug": "qiyam", "sectionId": "qiyam-witr", "displayPosition": 1, "label": "Qiyam" },
    { "slug": "witr", "sectionId": "qiyam-witr", "displayPosition": 2, "label": "Witr" },
    { "slug": "quran-memorisation", "sectionId": "quran", "displayPosition": 1, "label": "Quran memorisation" },
    { "slug": "quran-reading", "sectionId": "quran", "displayPosition": 2, "label": "Quran reading" },
    { "slug": "adhkar-1", "sectionId": "adhkar", "displayPosition": 1, "label": "Adhkar 1" },
    { "slug": "adhkar-2", "sectionId": "adhkar", "displayPosition": 2, "label": "Adhkar 2" },
    { "slug": "adhkar-3", "sectionId": "adhkar", "displayPosition": 3, "label": "Adhkar 3" },
    { "slug": "adhkar-4", "sectionId": "adhkar", "displayPosition": 4, "label": "Adhkar 4" },
    { "slug": "adhkar-5", "sectionId": "adhkar", "displayPosition": 5, "label": "Adhkar 5" },
    { "slug": "adhkar-6", "sectionId": "adhkar", "displayPosition": 6, "label": "Adhkar 6" },
    { "slug": "adhkar-7", "sectionId": "adhkar", "displayPosition": 7, "label": "Adhkar 7" },
    { "slug": "adhkar-8", "sectionId": "adhkar", "displayPosition": 8, "label": "Adhkar 8" },
    { "slug": "adhkar-9", "sectionId": "adhkar", "displayPosition": 9, "label": "Adhkar 9" },
    { "slug": "fast-voluntary", "sectionId": "fasting", "displayPosition": 1, "label": "Voluntary fast" },
    { "slug": "friday-1", "sectionId": "friday", "displayPosition": 1, "label": "Friday 1" },
    { "slug": "friday-2", "sectionId": "friday", "displayPosition": 2, "label": "Friday 2" },
    { "slug": "friday-3", "sectionId": "friday", "displayPosition": 3, "label": "Friday 3" },
    { "slug": "friday-4", "sectionId": "friday", "displayPosition": 4, "label": "Friday 4" },
    { "slug": "friday-5", "sectionId": "friday", "displayPosition": 5, "label": "Friday 5" },
    { "slug": "friday-6", "sectionId": "friday", "displayPosition": 6, "label": "Friday 6" },
    { "slug": "friday-7", "sectionId": "friday", "displayPosition": 7, "label": "Friday 7" }
  ],
  "taskVersions": [
    { "taskSlug": "fajr-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fajr-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fajr-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fajr-4", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fajr-5", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fajr-6", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "dhuhr-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "dhuhr-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "dhuhr-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "dhuhr-4", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "asr-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "asr-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "asr-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "maghrib-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "maghrib-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "maghrib-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "isha-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "isha-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "isha-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "qiyam", "catalogueVersion": 1, "points": 5, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "witr", "catalogueVersion": 1, "points": 4, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "quran-memorisation", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "quran-reading", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-1", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-2", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-3", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-4", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-5", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-6", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-7", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-8", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "adhkar-9", "catalogueVersion": 1, "points": 2, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "everyDay" } },
    { "taskSlug": "fast-voluntary", "catalogueVersion": 1, "points": 5, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["MONDAY", "THURSDAY"] } },
    { "taskSlug": "friday-1", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-2", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-3", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-4", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-5", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-6", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } },
    { "taskSlug": "friday-7", "catalogueVersion": 1, "points": 1, "maxOccurrencesPerDay": 1, "scheduleRule": { "type": "daysOfWeek", "days": ["FRIDAY"] } }
  ]
}
```

### 3d — Parsing

- [ ] T015 [TEST] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueJsonTest.kt`. Write a test `good fixture parses` that loads `/catalogue/good/valid-catalogue.json` from the classpath (via `javaClass.getResourceAsStream`), calls `parseCatalogue(json)`, and asserts the result is a success containing 1 version, 10 sections, 40 tasks, 40 taskVersions. Run `./gradlew :app:testDebugUnitTest`. **Expected: FAILS to compile** because `parseCatalogue` does not exist. That is the red state. Do not skip observing it.

- [ ] T016 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueJson.kt` with `fun parseCatalogue(json: String): Result<Catalogue>`. Use `Json { ignoreUnknownKeys = false; classDiscriminator = "type" }`. Wrap parsing in `runCatching`. Run `./gradlew :app:testDebugUnitTest`. **Expected: T015 now passes.**

### 3e — Validator skeleton and the positive control

- [ ] T017 [TEST] [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueValidatorTest.kt` with one test `valid catalogue has no defects`: parse the good fixture, call `CatalogueValidator().validate(catalogue)`, assert the returned list is empty. Run tests. **Expected: FAILS to compile** — `CatalogueValidator` does not exist.

- [ ] T018 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueValidator.kt` with `class CatalogueValidator { fun validate(catalogue: Catalogue): List<CatalogueDefect> = emptyList() }`. Run tests. **Expected: T017 passes.** The validator is deliberately empty — rules are added one at a time below.

### 3f — The 16 rules

Each rule follows the same three-step shape. **Do them one rule at a time, in order.** Do not write
several rules then several fixtures.

For each rule N below: (a) create the bad fixture by copying `good/valid-catalogue.json` and
applying *only* the stated change, (b) add a test asserting that fixture yields exactly the named
defect, observe it fail, (c) add the rule to `CatalogueValidator.validate` and observe it pass.

- [ ] T019 [US1] **Rule 1 — duplicate slug.** Fixture `bad/duplicate-slug.json`: change the `slug` of task `fajr-2` to `fajr-1` (and its taskVersion `taskSlug` likewise). Expect `DuplicateTaskSlug("fajr-1", 2)`.

- [ ] T020 [US1] **Rule 2 — malformed slug.** Fixture `bad/malformed-slug.json`: change task `asr-1`'s slug to `Asr_1` (and its taskVersion). Expect `MalformedSlug("Asr_1")`. Valid pattern: `^[a-z0-9]+(-[a-z0-9]+)*$`.

- [ ] T021 [US1] **Rule 3a — zero points.** Fixture `bad/zero-points.json`: set `points` to `0` for `fajr-1`. Expect `NonPositivePoints("fajr-1", 0)`.

- [ ] T022 [US1] **Rule 3b — negative points.** Fixture `bad/negative-points.json`: set `points` to `-2` for `fajr-1`. Expect `NonPositivePoints("fajr-1", -2)`.

- [ ] T023 [US1] **Rule 4 — occurrence limit.** Fixture `bad/zero-occurrences.json`: set `maxOccurrencesPerDay` to `0` for `witr`. Expect `InvalidOccurrenceLimit("witr", 0)`.

- [ ] T024 [US1] **Rule 5 — unknown section.** Fixture `bad/missing-section.json`: set `sectionId` of `isha-1` to `nonexistent`. Expect `UnknownSection("isha-1", "nonexistent")`.

- [ ] T025 [US1] **Rule 6 — duplicate position in section.** Fixture `bad/duplicate-position-in-section.json`: set `displayPosition` of `adhkar-2` to `1` (colliding with `adhkar-1`). Expect `DuplicateDisplayPosition("adhkar", 1, listOf("adhkar-1", "adhkar-2"))`. **Also add a test proving the opposite**: two tasks in *different* sections sharing position 1 is NOT a defect — the good fixture already contains many, so assert the good fixture stays clean.

- [ ] T026 [US1] **Rule 7 — duplicate section order.** Fixture `bad/duplicate-section-order.json`: set `order` of section `dhuhr` to `1`. Expect `DuplicateSectionOrder(1, listOf("fajr", "dhuhr"))`.

- [ ] T027 [US1] **Rule 8 — unreachable schedule.** Fixture `bad/unreachable-schedule.json`: set `friday-1`'s scheduleRule to `{ "type": "daysOfWeek", "days": [] }`. Expect `UnreachableSchedule("friday-1")`.

- [ ] T028 [US1] **Rule 9 — version/date order mismatch.** Fixture `bad/version-order-mismatch.json`: add a second version `{ "version": 2, "effectiveFrom": "2025-01-01" }` — earlier than version 1. Expect `VersionOrderMismatch(2, "2025-01-01")`.

- [ ] T029 [US1] **Rule 10 — duplicate effective-from.** Fixture `bad/duplicate-effective-from.json`: add `{ "version": 2, "effectiveFrom": "2026-01-01" }` — same date as version 1. Expect `DuplicateEffectiveFrom("2026-01-01", listOf(1, 2))`.

- [ ] T030 [US1] **Rule 11 — weekday totals.** Fixture `bad/wrong-weekday-total.json`: change `qiyam` points from `5` to `6`. Expect `WeekdayTotalMismatch` for every weekday (base day becomes 70). Rule: for each of the 7 weekdays compute available points; expected values are Mon 74, Tue 69, Wed 69, Thu 74, Fri 76, Sat 69, Sun 69.

- [ ] T031 [US1] **Rule 12 — week total.** Same rule pass as T030 but asserting the 7-day sum equals 500. Fixture `bad/wrong-week-total.json`: change `friday-1` points from `1` to `2`. Expect `WeekTotalMismatch(500, 501)`.

- [ ] T032 [US1] **Rule 13 — section composition.** Fixture `bad/wrong-section-composition.json`: delete task `adhkar-9` and its taskVersion. Expect `SectionCompositionMismatch("adhkar", 18, 16)`. Expected section totals on a base day: fajr 12, dhuhr 8, asr 6, maghrib 6, isha 6, qiyam-witr 9, quran 4, adhkar 18.

- [ ] T033 [US1] **Rule 14 — user authoring affordance.** Fixture `bad/user-editable-flag.json`: add `"editable": true` to task `fajr-1`. Expect a defect. Because `ignoreUnknownKeys = false`, this surfaces as a parse failure first — so this rule is enforced by the parser. Assert `MalformedCatalogue` is returned and that its message names the offending field. Add a comment in the test recording that FR-019 is enforced at the parse boundary.

- [ ] T034 [US1] **Rule 15 — malformed catalogue.** Fixture `bad/malformed.json`: content is the single line `{ "versions": [` (truncated, invalid JSON). Expect `MalformedCatalogue`.

- [ ] T035 [US1] **Rule 16 — no catalogue.** No fixture file. Add a test that calls the loader with a path that does not exist and asserts `NoCatalogue` is returned — **not** an empty list, and not an exception. This is FR-011.

### 3g — Cross-cutting guarantees

- [ ] T036 [US1] Add a test `validator never throws` in `CatalogueValidatorTest.kt` that runs `validate` against every fixture in `bad/` and asserts no exception escapes.

- [ ] T037 [US1] Add a test `validator reports all defects not just the first`: create fixture `bad/two-defects.json` with both a zero-points task and a duplicate section order, and assert the returned list contains both defect types.

- [ ] T038 [US1] Add a test `defect order is stable`: run `validate` on the same catalogue twice and assert the two lists are equal. Sort defects deterministically inside `validate` before returning.

- [ ] T039 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueArithmeticTest.kt` asserting, against the good fixture: Sat/Sun/Tue/Wed = 69, Mon/Thu = 74, Fri = 76, week sum = 500, and each section total from T032.

- [ ] T040 [US1] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/CatalogueMutationTest.kt`. Parse the good fixture, then **in memory** change one task's points from 2 to 3, and assert `validate` now returns `WeekdayTotalMismatch` and `WeekTotalMismatch`. Do not edit the fixture file on disk.

- [ ] T041 [US1] Run the full suite: `./gradlew :app:testDebugUnitTest`. **Expected**: all tests pass. Then manually verify the suite is real — edit `good/valid-catalogue.json` changing one `"points": 2` to `"points": 3`, re-run, confirm `CatalogueArithmeticTest` FAILS, then `git checkout -- app/src/test/resources/catalogue/good/valid-catalogue.json`.

**Checkpoint**: User Story 1 is complete and independently deliverable. This is the MVP.

---

## Phase 4: User Story 2 — Domain glossary (Priority: P2)

**Goal**: twelve terms, one definition each, no technology named.

**Independent test**: `docs/GLOSSARY.md` exists with 12 `##` headings; no definition names a
framework, database, or screen.

- [ ] T042 [P] [US2] Create `docs/GLOSSARY.md`. One `## <Term>` heading per term, in this order: Task Definition, Task Version, Section, Schedule Rule, Day Plan, Planned Task, Completion, Occurrence, Daily Score, Weekly Score, Consistency Day, Streak. Source the wording from the **Key Entities** section of [data-model.md](./data-model.md) and [spec.md](./spec.md). Each definition is 1–3 sentences. Name no technology — no Room, Kotlin, JSON, Compose, database, or screen. Explicitly contrast Task Definition vs Task Version, Day Plan vs Planned Task, and Completion vs Occurrence (FR-014).

- [ ] T043 [US2] Verify: `grep -c "^## " docs/GLOSSARY.md` returns `12`. Then read each definition and confirm no technology name appears.

**Checkpoint**: glossary complete and independently deliverable.

---

## Phase 5: User Story 3 — Recorded architectural decisions (Priority: P3)

**Goal**: the twelve decisions answered in place, and the spelling unified.

**Independent test**: `docs/PLAN.md` contains a section `Architectural Decisions (Recorded)` with 12
numbered entries, each stating a decision and a rationale; zero `catalog` spellings remain.

- [ ] T044 [US3] In `docs/PLAN.md`, rename the heading `# Architectural Decisions to Make Early` to `# Architectural Decisions (Recorded)`. Replace the introductory sentence "These genuinely block Phase 2 or are prohibitively expensive to reverse." with a sentence stating these are recorded decisions, not open questions.

- [ ] T045 [US3] Rewrite each of the 12 numbered items in that section so each states the decision taken plus a one-line rationale, replacing the current recommendation wording. Use these answers, which are already fixed elsewhere and must not be contradicted: (1) day boundary = local midnight to local midnight, per constitution Principle VII; (2) week = Saturday to Friday, one `WeekCalculator`, per Principle VII; (3) immutable Task Versions + materialised Day Plans + points denormalised onto completions, per Principle III; (4) append-only occurrence log, undo = tombstone latest; (5) client-generated UUIDs for completions, day plans and task versions — **Task Definition identity is a slug**, per spec clarification Q1; (6) sync-ready columns from day one, per Principle V; (7) sealed schedule rule with `DateAnchored` reserved; (8) Koin, sole and uncontested — no DI code exists to migrate; (9) `:domain` has zero Android and zero Room, repository interfaces live there, per Principle II; (10) Hijri snapshot stored per Day Plan; (11) injectable clock from the start, per Principle VII; (12) task text is Arabic data, and the interface shell language is a product decision recorded in the design, not settled here.

- [ ] T046 [US3] Correct the spelling throughout `docs/PLAN.md`: replace `catalog` with `catalogue` **only where not already followed by `ue`**. Use the regex `catalog(?!ue)`. There are 32 occurrences. Change no other file — the constitution and `CLAUDE.md` are already correct.

- [ ] T047 [US3] Verify: `grep -rn "catalog\([^u]\|$\)" docs/ CLAUDE.md .specify/memory/` returns **no output**. Then `grep -c "Architectural Decisions (Recorded)" docs/PLAN.md` returns `1`.

**Checkpoint**: decisions recorded, terminology unified.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T048 [P] Create `app/src/test/java/com/giraffe/mizanapp/catalogue/DomainPurityTest.kt`. Read every `.kt` file under `app/src/test/java/com/giraffe/mizanapp/catalogue/` as text and assert none contains the string `import android.`. This guards Principle II and keeps the Phase 2 move into `:domain` mechanical.

- [ ] T049 Verify no production code leaked. Run `git diff develop-v1... --stat -- app/src/main`. **Expected: empty output.** Any output means SC-007 is violated — find and remove it.

- [ ] T050 Verify the APK is untouched by the new dependency. Confirm `app/build.gradle.kts` contains `testImplementation(libs.kotlinx.serialization.json)` and does **not** contain an `implementation(libs.kotlinx.serialization.json)` line.

- [ ] T051 Run the full suite one final time: `./gradlew :app:testDebugUnitTest`. **Expected**: BUILD SUCCESSFUL, every test green.

- [ ] T052 Walk [quickstart.md](./quickstart.md) end to end, including the deliberate-break step in Scenario 4. Confirm each stated expectation holds.

---

## Dependencies

```text
Phase 1 Setup (T001-T004)
        |
Phase 2 Foundational (T005)
        |
        +-----------------+------------------+
        |                 |                  |
   Phase 3 US1       Phase 4 US2        Phase 5 US3
   (T006-T041)       (T042-T043)        (T044-T047)
        |                 |                  |
        +-----------------+------------------+
                          |
                Phase 6 Polish (T048-T052)
```

- **Setup blocks everything.** T004 must pass before any other phase.
- **US1, US2 and US3 are fully independent of each other.** US2 and US3 touch only `docs/`; US1
  touches only `app/src/test/`. They can be done in any order, or in parallel by different people.
- **Within US1 the order is strict.** T006→T041 is a chain. Only T006–T010 are parallelisable.
- **Polish requires all three stories** — T049 and T051 check the whole increment.

## Parallel opportunities

- T006, T007, T008, T009, T010 — five model files, no interdependencies. `[P]`
- T042 (US2) and T044–T046 (US3) can run alongside all of US1 — different directories entirely.
- Everything in 3f (T019–T035) is **strictly sequential**: each adds a rule to the same
  `CatalogueValidator.kt`.

## Implementation strategy

**MVP = User Story 1 alone (T001–T041).** It delivers the contract, which is the entire risk
reduction of this increment. US2 and US3 are documentation and can follow.

Suggested delivery: finish Phase 1 and 2, then US1 to T041 and confirm green. Then US2 and US3,
which are quick. Then Phase 6.

**If you get stuck**: stop and report which task number and what the actual versus expected output
was. Do not work around a failing task by changing the fixture arithmetic — the totals 69, 74, 76
and 500 are fixed by `docs/PLAN.md` and are never the thing that is wrong.
