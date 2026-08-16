---

description: "Task list for spec 007 — Identity & Cloud Sync"
---

# Tasks: Identity & Cloud Sync

**Input**: Design documents from `/specs/007-identity-cloud-sync/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: MANDATORY. The project constitution (Principle I, NON-NEGOTIABLE) forbids production code
before a failing test that requires it, and a pull request whose first commit is production code has
already violated it. Every test task below must be committed **before** the implementation task that
follows it.

**Organization**: grouped by user story so each can be implemented, tested and merged independently.

**Revision**: this list was rewritten after `/speckit-analyze`. Task IDs are sequential in true
execution order, so they do not match the first draft's numbering. Three ordering bugs that would
have physically blocked the build were fixed, and eleven missing test tasks were added.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: US1–US5, mapping to spec.md's user stories
- Every task names its exact file path

---

## Conventions for the implementer — read this before T001

These rules remove every judgement call in the task list. Follow them literally.

### 1. The stub → red → green cycle

Kotlin is statically typed, so a test cannot compile against a function that does not exist. Each
unit therefore gets **two tasks**:

- **Task A (test task)** — create the production file containing only the type declarations and the
  function signature, with the body `TODO("T0XX")`, **and** write the full test file. Run the test.
  It MUST fail with `NotImplementedError` or an assertion failure. Commit. The stub is not an
  implementation; the failing test is the deliverable.
- **Task B (implementation task)** — replace the `TODO(...)` body with the real implementation.
  Re-run the same test. It MUST pass. Change nothing in the test file. Commit.

If a test passes in Task A, the test is wrong — fix the test, do not proceed.

### 2. Commands

Run from the repository root, Windows PowerShell:

```powershell
.\gradlew :domain:test                                              # JVM, fast
.\gradlew :app:test                                                 # JVM, fast
.\gradlew :data:connectedAndroidTest                                # needs a running emulator
.\gradlew :app:connectedAndroidTest                                 # needs a running emulator
.\gradlew :domain:test --tests "*MergeDayRecordTest*"               # one class
.\gradlew :data:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.giraffe.mizanapp.data.OutboxIdempotencyTest
```

### 3. Files that MUST NOT be modified by any task in this list

Touching one of these means the task was misread. If a change seems necessary, stop and re-read the
task.

```text
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CompletionRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/DayPlanRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CatalogueRepository.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/DayPlan.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/Completion.kt
domain/src/main/kotlin/com/giraffe/mizanapp/domain/day/BuildDayPlan.kt
data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCompletionRepository.kt
data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomDayPlanRepository.kt
data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomCatalogueRepository.kt
data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/1.json
data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/2.json
```

`RoomCompletionRepository` and `RoomDayPlanRepository` are wrapped by decorators, never edited.
Existing schema JSON files are historical records — a new `3.json` is generated, the old two stay
byte-identical.

### 4. The one rule that outranks every other instruction here

**A day already materialised on this device is never re-derived, re-versioned, or rewritten — by
sync, by a merge, by a catalogue pull, by anything** (FR-024a, Principle III, which admits no
exception). No task in this list creates a method capable of it. If an implementation seems to
require one, the task has been misread. Specifically: there is no `adoptMergedVersion`, no
`updateMergedVersion`, and no `DayPlanDao` method that can write a date, a catalogue version, a
points value, or an available-points total.

### 5. Kotlin style, matching the existing code

- 4-space indent, no wildcard imports, imports sorted alphabetically, trailing commas in multi-line
  parameter lists.
- Every new public type and every non-obvious function gets a KDoc comment that says **why**, not
  what. Match the density of the surrounding files (see `CompletionDao.kt` for the house style).
- `:domain` may import only Kotlin stdlib, `kotlinx.coroutines`, `kotlinx.serialization` and
  `java.time`. **No** `android.*`, `androidx.*`, `io.ktor.*`, `io.github.jan.*`, `org.koin.*`.
- Never read `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()` or `ZoneId.systemDefault()`
  anywhere. Inject `TimeProvider` (Principle VII). Tests use the existing
  `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/FakeTimeProvider.kt`.

### 6. Forbidden words in any user-visible string (Principle IX)

`failed`, `failure`, `error`, `lost`, `missing`, `problem`, `wrong`, `you didn't`, `you haven't`,
`retry now`. No red, orange, or amber colour value anywhere in this increment. Status text is a
statement of fact about the queue, never about the person.

### 7. Commit message per task

```text
<type>(007): <task id> <short description>

e.g.  test(007): T017 failing test for MergeDayRecord min-version merge
      feat(007): T018 implement MergeDayRecord
```

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependencies, build configuration, and a **working, verified remote schema** — the
backend must exist before US1 tries to sign into it.

- [X] T001 Add the new versions and libraries to `gradle/libs.versions.toml`. Under `[versions]` add `supabase = "3.5.0"`, `ktor = "3.3.0"`, `work = "2.10.1"`, `koinWork = "4.1.0"`. Under `[libraries]` add `supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }`, `supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }`, `supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }`, `ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }`, `androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }`, `androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }`, `koin-androidx-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager", version.ref = "koinWork" }`. Add no plugin. Run `.\gradlew help` and confirm the catalogue parses.
- [X] T002 Wire the new dependencies into `data/build.gradle.kts`: inside `dependencies`, add `implementation(platform(libs.supabase.bom))`, `implementation(libs.supabase.auth)`, `implementation(libs.supabase.postgrest)`, `implementation(libs.ktor.client.okhttp)`, `implementation(libs.androidx.work.runtime)`, `implementation(libs.kotlinx.serialization.json)`, and `androidTestImplementation(libs.androidx.work.testing)`. Inside `android { }` add `buildFeatures { buildConfig = true }`. Apply `alias(libs.plugins.kotlin.serialization)` in the `plugins` block. Remove nothing.
- [X] T003 Add the Supabase configuration to `data/build.gradle.kts`. Read `local.properties` if present and expose two `BuildConfig` fields on `defaultConfig`: `buildConfigField("String", "SUPABASE_URL", "\"${props.getProperty("SUPABASE_URL", "")}\"")` and the same for `SUPABASE_ANON_KEY`. **Both must default to an empty string when absent** — the build must succeed with no `local.properties` at all.
- [X] T004 [P] Add `implementation(libs.koin.androidx.workmanager)` to `app/build.gradle.kts` dependencies. Change nothing else in that file.
- [X] T005 [P] Create `supabase/migrations/0001_identity_cloud_sync.sql` as a byte-identical copy of `specs/007-identity-cloud-sync/contracts/remote-schema.sql`. Do not edit the SQL.
- [X] T006 [P] Create `supabase/seed/catalogue_publication.sql`: a single `insert into public.catalogue_publications (version, effective_from, format_version, payload) values (1, date '2026-01-01', 1, $$<contents of domain/src/main/resources/catalogue/valid-catalogue.json>$$::jsonb);`. The payload must be the file's exact contents.
- [X] T007 Apply the schema to a real project — **US1 cannot be built or validated without it**. Run `supabase link --project-ref <ref>`, `supabase db push`, then `supabase db execute --file supabase\seed\catalogue_publication.sql`. Verify one row exists in `catalogue_publications` with `format_version = 1`. Put `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties` (never a service-role key, which bypasses row-level security).
- [X] T008 Verify row-level security **now, not at the end** (SC-008): `supabase db execute --file specs\007-identity-cloud-sync\contracts\rls-verification.sql`. It must print `RLS OK`. Any `SC-008 FAILED` blocks all further work — the isolation guarantee has to hold before any real user record is uploaded, not after. T137 re-runs this as the merge gate.
- [X] T009 Temporarily rename `local.properties`, run `.\gradlew :app:assembleDebug` and confirm it succeeds with no keys, then restore it and run `.\gradlew :domain:test :app:test`. All green. **Phase 1 checkpoint.**

**Checkpoint**: the project builds with and without backend configuration, the remote schema is live, and cross-account isolation is proven.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the types, storage, and remote seam every user story needs.

**⚠️ CRITICAL**: no user story work may begin until T051 passes.

### Domain types (no behaviour, so no test task)

- [X] T010 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/identity/AccountSession.kt` with the sealed interface `AccountSession` and its two members `SignedOut` (data object) and `SignedIn(userId: String, email: String, displayName: String?)`, exactly as in [data-model.md](./data-model.md) §1.
- [X] T011 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/identity/SignInStep.kt` with the sealed interface `SignInStep`, its six members and the `CodeRejection` enum, exactly as in [data-model.md](./data-model.md) §1. Every member carries `email: String` — this is what FR-002a's "never discards the entered address" rests on.
- [X] T012 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/identity/SignOutMode.kt` with `enum class SignOutMode { KEEP_LOCAL_RECORDS, REMOVE_LOCAL_RECORDS }`.
- [X] T013 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/SyncStatus.kt` with the sealed interface `SyncStatus` and its five members (`NotSignedIn`, `UpToDate`, `Pending(count: Int)`, `NotSyncing`, `LoadingEarlierDays(knownFrom: LocalDate?)`).
- [X] T014 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/DayRecord.kt` with `data class DayRecord(val date: LocalDate, val catalogueVersion: Int)`. The syncable projection of a day — it deliberately holds no tasks and no points total.

### Pure functions — stub + failing test, then implement

- [X] T015 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/RecordCoverage.kt` with `data class RecordCoverage(val knownFrom: LocalDate?, val complete: Boolean)`, `fun isKnown(date: LocalDate): Boolean = TODO("T016")`, and `companion object { fun completeFrom(recordStart: LocalDate?) = RecordCoverage(recordStart, complete = true) }`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/sync/RecordCoverageTest.kt`: complete coverage returns true for every date; incomplete coverage returns false before `knownFrom`, true at `knownFrom` and after; a null `knownFrom` returns true. Run it — MUST fail.
- [X] T016 [P] Implement `isKnown` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/RecordCoverage.kt` as `complete || knownFrom == null || !date.isBefore(knownFrom)`. Re-run T015's test — MUST pass. Do not edit the test.
- [X] T017 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/MergeDayRecord.kt` with `fun mergeDayRecord(local: DayRecord?, remote: DayRecord?): DayRecord = TODO("T018")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/sync/MergeDayRecordTest.kt` asserting: local-only returns local; remote-only returns remote; both present returns the **lower** `catalogueVersion`; the result is the same with the arguments swapped; merging a result with either input returns the result; both null throws `IllegalArgumentException`. Run it — MUST fail.
- [X] T018 [P] Implement `mergeDayRecord` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/MergeDayRecord.kt` using `minOf` on `catalogueVersion`. Add a KDoc stating that this decides what the **account** stores and what a device derives when it materialises a date for the first time, and that it never reaches a day already recorded on a device (FR-024a, FR-024b). Re-run T017's test.
- [X] T019 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/MergeCompletion.kt` with `fun mergeCompletion(local: Completion?, remote: Completion?): Completion = TODO("T020")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/sync/MergeCompletionTest.kt` asserting: local-only returns local; remote-only returns remote; if either side has a non-null `reversedAt` the result is reversed; a null `reversedAt` never clears a tombstone; the result is identical with the arguments swapped; `pointsAwarded`, `recordedAt`, `creditedDate` and `taskSlug` are never recomputed. Run it — MUST fail.
- [X] T020 [P] Implement `mergeCompletion` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/MergeCompletion.kt`: `reversedAt = local?.reversedAt ?: remote?.reversedAt`, every other field write-once from the non-null side. Re-run T019's test.
- [X] T021 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/RetrySchedule.kt` with `object RetrySchedule { fun nextAttemptAt(attempt: Int, from: Instant): Instant = TODO("T022") }`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/sync/RetryScheduleTest.kt`: attempt 0 gives roughly 30 seconds; each attempt at least doubles until the cap; the delay caps at 6 hours; **attempt 10 000 still returns a finite instant** (FR-021a). The object must expose no `shouldDrop`, `maxAttempts` or expiry. Run it — MUST fail.
- [X] T022 [P] Implement `RetrySchedule.nextAttemptAt` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/RetrySchedule.kt`: exponential from 30 s, capped at 6 h. Re-run T021's test.
- [X] T023 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/DeriveSyncStatus.kt` with `fun deriveSyncStatus(session: AccountSession, pendingCount: Int, reachable: Boolean, coverage: RecordCoverage): SyncStatus = TODO("T024")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/sync/DeriveSyncStatusTest.kt` covering every row of [contracts/sync-engine.md](./contracts/sync-engine.md) §8, including that incomplete coverage outranks a non-zero pending count. Run it — MUST fail.
- [X] T024 Implement `deriveSyncStatus` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/sync/DeriveSyncStatus.kt` following the precedence table exactly: signed out → `NotSignedIn`; coverage incomplete → `LoadingEarlierDays`; pending and reachable → `Pending`; pending and unreachable → `NotSyncing`; otherwise `UpToDate`. Re-run T023's test.

### Domain repository interfaces (declarations only — their implementations get the tests)

- [X] T025 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/AccountRepository.kt` with the interface and the `CodeRequest` / `CodeConfirmation` sealed interfaces, copied exactly from [contracts/repositories.md](./contracts/repositories.md) — including `confirmCode(email, code, replaceLocalRecords: Boolean = false)` and `WouldReplaceLocalRecords(currentEmail, recordedDays, completionCount, unsyncedCount)`. Include the KDoc from that file.
- [X] T026 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/SyncRepository.kt` with `observeStatus()`, `observePendingCount()` and `syncNow()`.
- [X] T027 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/RecordCoverageRepository.kt` with `observeCoverage(): Flow<RecordCoverage>` and `suspend fun coverage(): RecordCoverage`.
- [X] T028 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/repository/CataloguePublicationRepository.kt` with `pullIfNewer(): PullOutcome` and the `PullOutcome` sealed interface.
- [X] T029 Run `.\gradlew :domain:test`. Everything passes, and `domain/src/test/kotlin/com/giraffe/mizanapp/domain/ModuleBoundaryTest.kt` still passes — no Android, Ktor, Koin or Supabase import has entered `:domain`.

### Local storage — Room migration 2 → 3

> **Order matters here and was wrong in the first draft.** Entities and DAOs must exist before the
> database declares them, and the database must be at version 3 before the migration test can run at
> all — `MigrationTestHelper` needs the exported `3.json`. Do not reorder T030–T040.

- [X] T030 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/SyncEntities.kt` containing `OutboxEntity`, `SyncCursorEntity` and `AccountScopeEntity` with exactly the columns in [data-model.md](./data-model.md) §2. `OutboxEntity.id` is the primary key and holds the deterministic string `"$entityType:$entityId:$operation"`; annotate `@Entity(tableName = "outbox", indices = [Index("nextAttemptAt")])`. `AccountScopeEntity` has `@PrimaryKey val id: Int = 0` and holds exactly one row.
- [X] T031 Add `val syncedAt: Long? = null` to both `DayPlanEntity` and `CompletionEntity` in `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/DayEntities.kt`. Add nothing else and change no existing column.
- [X] T032 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/OutboxDao.kt` with: `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: OutboxEntity)`, `@Query("SELECT * FROM outbox WHERE nextAttemptAt <= :now ORDER BY createdAt LIMIT :limit") suspend fun due(now: Long, limit: Int): List<OutboxEntity>`, `@Query("DELETE FROM outbox WHERE id IN (:ids)") suspend fun remove(ids: List<String>)`, `@Query("UPDATE outbox SET attempts = attempts + 1, nextAttemptAt = :at WHERE id IN (:ids)") suspend fun defer(ids: List<String>, at: Long)`, `@Query("SELECT COUNT(*) FROM outbox") fun observeCount(): Flow<Int>`, `@Query("SELECT COUNT(*) FROM outbox") suspend fun count(): Int`, and `@Query("DELETE FROM outbox") suspend fun clear()`. There is deliberately **no** method that deletes by age or caps the table (FR-021a); add a KDoc saying so.
- [X] T033 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/SyncCursorDao.kt` with an upsert, `get(key: String): String?`, and `clear()`.
- [X] T034 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/AccountScopeDao.kt` with an upsert of the single row, `observe(): Flow<AccountScopeEntity?>`, `get(): AccountScopeEntity?` and `clear()`.
- [X] T035 Add to `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/CompletionDao.kt`, changing no existing method: `@Query("UPDATE completions SET userId = :userId, updatedAt = :at WHERE userId IS NULL") suspend fun claimForUser(userId: String, at: Long): Int`, `@Query("UPDATE completions SET syncedAt = :at WHERE id IN (:ids)") suspend fun markSynced(ids: List<String>, at: Long)`, `@Query("SELECT * FROM completions WHERE syncedAt IS NULL") suspend fun unsynced(): List<CompletionEntity>`, `@Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnoring(row: CompletionEntity): Long`, `@Query("UPDATE completions SET reversedAt = :at, updatedAt = :at WHERE id = :id AND reversedAt IS NULL") suspend fun applyTombstone(id: String, at: Long)`, and `@Query("DELETE FROM completions") suspend fun clear()`. KDoc on `claimForUser`: the `IS NULL` guard is what makes it idempotent and what stops a record ever changing accounts (FR-013). KDoc on `applyTombstone`: it can only ever set a tombstone, never clear one (FR-018).
- [X] T036 Add to `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/DayPlanDao.kt`, changing no existing method: `claimPlansForUser(userId, at)` (`UPDATE day_plans … WHERE userId IS NULL`), `claimPlannedTasksForUser(userId, at)` (`UPDATE planned_tasks … WHERE userId IS NULL`), `markSynced(dates: List<String>, at: Long)` (`syncedAt` only), `@Query("SELECT * FROM day_plans WHERE syncedAt IS NULL") suspend fun unsynced(): List<DayPlanEntity>`, and `clear()` / `clearPlannedTasks()`. **Add no method that can write `date`, `catalogueVersion`, `availablePoints`, or a planned task's points** — see Conventions §4. Update the class KDoc: the only writable columns are `userId`, `updatedAt` and `syncedAt`; a recorded day's figures are unreachable from this interface, which is how FR-024a is enforced structurally rather than by discipline.
- [X] T037 Bump `MizanDatabase` in `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt` to `version = 3`, add the three new entities, and declare `abstract fun outboxDao(): OutboxDao`, `abstract fun syncCursorDao(): SyncCursorDao`, `abstract fun accountScopeDao(): AccountScopeDao`. Add `MIGRATION_2_3` to `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Migrations.kt` with an **empty** `migrate` body for now, and register it in `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabaseFactory.kt` beside `MIGRATION_1_2`. Never add `fallbackToDestructiveMigration`. Build once so KSP exports `3.json`.
- [X] T038 Extend `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/MizanDatabaseMigrationTest.kt` with a `migrate2To3` test: open a v2 database, insert one day plan with three planned tasks and four completions with known figures, run `MIGRATION_2_3`, then assert (a) every plan, planned task and completion is still present with **identical** figures, (b) `outbox`, `sync_cursors` and `account_scope` exist and are empty, (c) `day_plans.syncedAt` and `completions.syncedAt` exist and are null. Run it — MUST fail on a missing table.
- [X] T039 Implement `MIGRATION_2_3` in `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Migrations.kt` with the exact DDL in [data-model.md](./data-model.md) §2 — three `CREATE TABLE`, two `ALTER TABLE … ADD COLUMN`, one `CREATE INDEX`. **Purely additive: no DROP, no RENAME, no UPDATE, no data rewrite.** Add a KDoc saying so, in the style of `MIGRATION_1_2`.
- [X] T040 Re-run T038 — MUST pass. Commit `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/3.json`. Confirm `1.json` and `2.json` are untouched (`git diff --stat -- data/schemas`).

### The remote seam

- [X] T041 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/dto/RemoteDtos.kt` with `RemoteDayRecord`, `RemoteCompletion`, `RemoteProfile` and `RemotePublication`, exactly as in [contracts/remote-data-source.md](./contracts/remote-data-source.md), all `@Serializable` with the `@SerialName` snake_case mappings. `RemoteCompletion` MUST NOT have a `dayPlanId` field.
- [X] T042 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/RemoteDataSource.kt` with the interface, `RemoteChanges` and `RemoteResult`, exactly as in [contracts/remote-data-source.md](./contracts/remote-data-source.md). No Supabase or Ktor import may appear in this file.
- [X] T043 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/NoOpRemoteDataSource.kt`: implements `RemoteDataSource` with every method returning `RemoteResult.Unreachable`. Bound whenever the build carries no Supabase configuration, so the app is the offline MVP and **no caller ever handles a null data source**. Add a KDoc saying it exists to keep the Koin binding non-nullable.
- [X] T044 Create `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/FakeRemoteDataSource.kt` implementing `RemoteDataSource` in memory, with the behaviours in the table at the end of [contracts/remote-data-source.md](./contracts/remote-data-source.md): upsert on the declared conflict target; `LEAST()` on `catalogueVersion`; `COALESCE()` on `reversedAt` with every other completion field frozen after first write; per-user row scoping; a monotonic `updatedAt` counter; injectable failures via `var unreachable: Boolean`, `var rejectIds: Set<String>`, `var dropAfter: Int?`, `var acknowledgeButDiscard: Boolean`; and a read counter so tests can assert nothing was re-fetched. Add `fun rows(): Pair<List<RemoteDayRecord>, List<RemoteCompletion>>`.
- [X] T045 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SupabaseClientFactory.kt` exposing `fun createSupabaseClient(): SupabaseClient?` — returns null when `BuildConfig.SUPABASE_URL` or `BuildConfig.SUPABASE_ANON_KEY` is blank. Install `Auth` and `Postgrest`; use the OkHttp engine. This file and T046's are the only two in the repository allowed to import `io.github.jan.*` or `io.ktor.*`.
- [X] T046 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SupabaseRemoteDataSource.kt` implementing every method of `RemoteDataSource` against Postgrest, with the conflict targets named in [contracts/remote-data-source.md](./contracts/remote-data-source.md) (`day_records` on `user_id,date`, `completions` on `id`, `profiles` on `id`). Map every thrown exception to a `RemoteResult`: connection/timeout/5xx → `Unreachable`, 401/403 with an unrenewable session → `NotAuthenticated`, other 4xx → `Rejected(reason, entityIds)`. **No method may throw.**

### Outbox and account scope

- [X] T047 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/Outbox.kt` containing the `OutboxEntry` data class **exactly as declared in [contracts/sync-engine.md](./contracts/sync-engine.md) §1** (including its derived `id` and the two enums) and the `Outbox` class with every method body `TODO("T048")`. Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/OutboxIdempotencyTest.kt`: enqueueing the same `(entityType, entityId, operation)` five times leaves exactly one row carrying the newest payload; `due()` returns oldest-`createdAt` first; `accepted()` is the only method that removes a row; `deferred()` increments `attempts` and moves `nextAttemptAt` without removing anything. Run it — MUST fail.
- [X] T048 Implement `Outbox` in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/Outbox.kt`. `enqueue` uses `OutboxEntry.id` and calls `OutboxDao.upsert`. Timestamps come from the injected `TimeProvider`. Re-run T047's test.
- [X] T049 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/AccountScope.kt`: a small class over `AccountScopeDao` exposing `observe(): Flow<AccountSession>`, `suspend fun current(): AccountSession`, `suspend fun set(userId: String, email: String, displayName: String?)`, `suspend fun setDisplayName(name: String?)` and `suspend fun clear()`. Maps the single row to `AccountSession.SignedIn` or `SignedOut`. **`clear()` empties only this table — it never touches a record.**
- [X] T050 Update `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`, adding to `dataModule`: `single { Outbox(get(), get()) }`, `single { AccountScope(get()) }`, and `single<RemoteDataSource> { createSupabaseClient()?.let { SupabaseRemoteDataSource(it) } ?: NoOpRemoteDataSource() }` — **non-nullable**. Add nothing to `domainModule` or `appModule` yet. DI wiring is Principle I's one exemption, so this task has no test.
- [X] T051 Run `.\gradlew :domain:test :app:test` and `.\gradlew :data:connectedAndroidTest`. All green. **Phase 2 checkpoint.**

**Checkpoint**: storage, the remote seam, the outbox and the merge rules all exist and are tested.

---

## Phase 3: User Story 1 — Sign in and keep every existing record (Priority: P1) 🎯 MVP

**Goal**: a user with weeks of local-only history creates an account, signs in, and every day,
completion and score is still there — unchanged — and is now in the account.

**Independent Test**: seed a device with several weeks of local history, sign in, and verify every
past day renders identical earned/available totals before and after; then uninstall, reinstall, sign
in on the same account, and verify the same figures return.

### Tests for User Story 1 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T052 [P] [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/RequestSignInCode.kt` and `.../ConfirmSignInCode.kt`, each with a single `suspend operator fun invoke(...)` body of `TODO("T061")`. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/ConfirmSignInCodeTest.kt` against a fake `AccountRepository`: a correct code returns `SignedIn` and calls `SyncRepository.syncNow()` exactly once; an expired code returns `CodeNotAccepted(EXPIRED)` and calls `syncNow()` **zero** times; an incorrect code likewise; `WouldReplaceLocalRecords` opens no session and calls `syncNow()` zero times; every outcome preserves the email; no outcome throws. Run it — MUST fail.
- [X] T053 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SignInMigrationTest.kt` against `FakeRemoteDataSource`: seed 21 days of plans and ~200 completions, record each day's earned and available totals, run the sign-in migration, then assert — (a) every local row now has `userId` set, **including `planned_tasks`**, (b) not one local figure changed, (c) the fake holds exactly one remote row per day and per completion, (d) re-running the whole migration changes nothing anywhere. Run it — MUST fail.
- [X] T054 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SignInMigrationResumeTest.kt`: run the migration with `FakeRemoteDataSource.dropAfter` set to 5, 50 and 150 rows in three separate cases; after each, re-run to completion and assert exactly one copy of every record exists remotely and every local row is intact. Also cover `acknowledgeButDiscard = true` (the ambiguous-failure case, US2 AS4). Run it — MUST fail.
- [X] T055 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SignInUnionTest.kt` (US1 AS5): pre-load `FakeRemoteDataSource` with records for dates the device does not have, and a different set for dates it does. Sign in, migrate, pull. Assert the account holds the **union** and that no record from either side was discarded.
- [X] T056 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SessionPersistenceTest.kt` (FR-005): after a successful sign-in, discard and rebuild the repository graph to simulate process death, and assert `observeSession()` emits `SignedIn` with the same `userId` **without any user action**; assert a session whose access token is near expiry is renewed silently and emits no `SignedOut` in between; assert the restored session is usable for a drain. Run it — MUST fail.
- [X] T057 [P] [US1] Write `app/src/test/java/com/giraffe/mizanapp/auth/SignInViewModelTest.kt` covering every transition in [contracts/auth.md](./contracts/auth.md): email → requesting → awaiting; resend inert before `resendAvailableAt`; expired code returns to `AwaitingCode` **with the email intact**; incorrect code likewise; offline gives `NeedsConnection` and never clears the email; `configured = false` renders the unavailable state and nothing else. Run it — MUST fail.
- [X] T058 [P] [US1] Write `app/src/androidTest/java/com/giraffe/mizanapp/auth/SignInScreenTest.kt`: the four visible states render; the email field keeps its value across a failed code entry; the resend control states when it becomes available; attempting sign-in offline shows the connection statement and leaves the app navigable (US1 AS3); **no password field exists in the hierarchy** and no node is a password-masked input (FR-002); no forbidden word from Conventions §6 appears in any node's text. Run it — MUST fail.
- [X] T059 [P] [US1] Extend `app/src/test/java/com/giraffe/mizanapp/NavigationRoutingTest.kt` with a `SIGNIN` case: `encode`/`decode` round-trips `Destination.SignIn`, a stack containing it survives the `StackSaver` save/restore cycle, and an unrecognised token still falls back to `Today`. Run it — MUST fail.
- [X] T060 [P] [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SyncStatusRepositoryTest.kt`: `OutboxSyncRepository.observePendingCount()` tracks the outbox exactly; `observeStatus()` yields `NotSignedIn` while signed out, `Pending(n)` with a queue and a reachable remote, `NotSyncing` with a queue and `unreachable = true`, and `UpToDate` on an empty queue; `syncNow()` **returns without suspending on the remote**. Run it — MUST fail.

### Implementation for User Story 1

- [X] T061 [US1] Implement `RequestSignInCode` and `ConfirmSignInCode` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/`. `ConfirmSignInCode(accounts, sync)` calls `confirmCode` and, **only on `CodeConfirmation.SignedIn`**, calls `sync.syncNow()`. Neither throws. Re-run T052.
- [X] T062 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/SupabaseAccountRepository.kt` implementing `AccountRepository` over `SupabaseClient.auth` and `AccountScope`. `requestCode` uses `signInWith(OTP) { createUser = true }` — **sign-up and sign-in are the same action** (FR-001, [contracts/auth.md](./contracts/auth.md)); there is no `signInWith(Email)` call and no password parameter anywhere (FR-002). `confirmCode` verifies the OTP. `observeSession` combines Supabase's session status with `AccountScope`, restoring across process death (FR-005). A missing client maps to `CodeRequest.NeedsConnection`. `signOut(KEEP_LOCAL_RECORDS)` ends the session and leaves `AccountScope` and every record in place. Leave `signOut(REMOVE_LOCAL_RECORDS)` and the `replaceLocalRecords` branch as `TODO("T125")` / `TODO("T126")` — US5 owns them. Re-run T056.
- [X] T063 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt` with `suspend fun claimLocalRecords(userId: String)`: call `DayPlanDao.claimPlansForUser`, `DayPlanDao.claimPlannedTasksForUser`, then `CompletionDao.claimForUser`, each guarded by `WHERE userId IS NULL`, all inside one `withTransaction`. Delete nothing, recompute nothing, overwrite nothing (FR-010).
- [X] T064 [US1] Add `suspend fun enqueueUnsynced()` to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt`: read `DayPlanDao.unsynced()` and `CompletionDao.unsynced()` and enqueue one `DAY_RECORD` entry per date and one `COMPLETION` entry per completion, **oldest date first**. Idempotent by `OutboxEntry.id`.
- [X] T065 [US1] Add `suspend fun drain(): DrainOutcome` to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt`, following [contracts/sync-engine.md](./contracts/sync-engine.md) §2 exactly: batches of 200 oldest-first; on `Ok` call `Outbox.accepted` **and** `markSynced`; on `Unreachable` defer the batch with `RetrySchedule` and stop the run; on `Rejected` defer only the named ids and continue; on `NotAuthenticated` stop (T086 completes that path). `syncedAt` is set only after acceptance (FR-012).
- [X] T066 [US1] Add `suspend fun migrateOnSignIn(userId: String)` to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt` running, in order: set scope → `claimLocalRecords` → `enqueueUnsynced` → `drain`. Each step must be safe to re-run alone (research R7). Re-run T053, T054 and T055 — all three MUST now pass.
- [X] T067 [US1] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/OutboxSyncRepository.kt` implementing `SyncRepository`: `observePendingCount()` from `OutboxDao.observeCount()`; `observeStatus()` combines session, pending count, reachability and coverage through `deriveSyncStatus`; `syncNow()` launches the engine on an application-scoped coroutine and **returns immediately**. Until T102 exists, coverage is `RecordCoverage.completeFrom(earliestPlanDate())`. Re-run T060.
- [X] T068 [US1] Create `app/src/main/java/com/giraffe/mizanapp/auth/SignInUiState.kt` and `SignInViewModel.kt` per [contracts/ui-state.md](./contracts/ui-state.md): one immutable state as `StateFlow`, no mutable state exposed, one `onEvent(SignInEvent)`. Re-run T057.
- [X] T069 [US1] Create `app/src/main/java/com/giraffe/mizanapp/auth/SignInScreen.kt` rendering `SignInUiState`. Existing theme tokens; no red; the connection state is body text, not a dialog; no password field. Re-run T058.
- [X] T070 [US1] Add `Destination.SignIn` to `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`: the object, `"SIGNIN"` in `encode`/`decode`, a `SignInRoute`, and an `AppRoute` branch. Add an entry point on `TodayScreen` that pushes it. **Never a start destination, never automatic.** Re-run T059.
- [X] T071 [US1] Register in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`: `single<AccountRepository> { SupabaseAccountRepository(...) }`, `single<SyncRepository> { OutboxSyncRepository(...) }`, `single { SyncEngine(...) }`, `factory { RequestSignInCode(get()) }`, `factory { ConfirmSignInCode(get(), get()) }`, `viewModel { SignInViewModel(get(), get(), get()) }`.
- [ ] T072 [US1] Run all four suites, then quickstart §4 SC-001 by hand: record several days signed out, note every total, sign in, confirm nothing changed, uninstall, reinstall, sign in, confirm the figures return.

**Checkpoint**: a user can sign in and their whole local history is safely in the account. MVP of the increment.

---

## Phase 4: User Story 2 — Everyday recording keeps working, and syncs when it can (Priority: P2)

**Goal**: recording and undoing stay instant and offline; whatever is recorded reaches the account on
its own; a neutral status surface says where things stand.

**Independent Test**: offline, record and undo a mixture of completions across several days; restore
connectivity; the account ends up holding exactly the set visible on the device, and the status
surface moves from pending to synced.

### Tests for User Story 2 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T073 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/OfflineRecordingUnaffectedTest.kt` (FR-014, SC-002): with `FakeRemoteDataSource.unreachable = true`, record and undo 200 times through the decorated `CompletionRepository`. Assert **structurally**, not by timing ratio: the fake recorded zero invocations; the decorator class has no `RemoteDataSource` constructor parameter (assert by reflection on its constructor); every call returned the same outcome type as the undecorated repository; and every call completed under a generous absolute ceiling of 250 ms. Run it — MUST fail.
- [X] T074 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/UndoTombstoneSyncTest.kt` (FR-018, US2 AS3): record a completion, sync, undo it, sync again; assert the remote row exists with a non-null `reversed_at` and that **no delete was ever issued**; apply the remote rows onto a second database and assert the completion does not reappear as live. Run it — MUST fail.
- [X] T075 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/OutboxDurabilityTest.kt` (FR-015, FR-021a, SC-010): enqueue 25 000 entries representing a year of daily recording; close and reopen the database and assert every entry survives; **additionally close the database, delete and rebuild the whole object graph as a device restart would, and assert the queue is still intact**; advance the fake clock by a year with the remote unreachable and assert the count is still 25 000 — nothing expired, evicted, or capped; then make the remote reachable and assert the whole queue drains. Run it — MUST fail.
- [X] T076 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SessionExpiryTest.kt` (FR-006, US2 AS6): make the fake return `RemoteResult.NotAuthenticated` mid-drain. Assert the session ends and `observeSession()` emits `SignedOut`; **every day plan, planned task and completion is still present with identical figures**; the outbox still holds every undrained entry; `account_scope` is untouched; recording still works; and the app offers sign-in rather than hiding history. Run it — MUST fail.
- [X] T077 [P] [US2] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BackgroundSyncSchedulingTest.kt` (FR-016, SC-003) using `WorkManagerTestInitHelper` with a synchronous executor: record a completion while unreachable, assert unique work named `"mizan-sync"` is enqueued with a `CONNECTED` constraint; satisfy the constraint via `TestDriver.setAllConstraintsMet`; assert the worker runs, the queue drains and `syncedAt` is set — with no screen opened and no user action. Run it — MUST fail.
- [X] T078 [P] [US2] Write `app/src/test/java/com/giraffe/mizanapp/sync/SyncStatusCopyTest.kt`: enumerate every `SyncStatus` and assert its rendered string matches the table in [contracts/ui-state.md](./contracts/ui-state.md) and contains none of the forbidden words in Conventions §6. Run it — MUST fail.
- [X] T079 [P] [US2] Write `app/src/androidTest/java/com/giraffe/mizanapp/sync/SyncStatusBarTest.kt`: each status renders its expected text; `NotSignedIn` renders **nothing at all**; the bar is not clickable; no colour it uses is red, orange or amber; it never covers or disables a recording control. Run it — MUST fail.

### Implementation for User Story 2

- [X] T080 [US2] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/SyncingCompletionRepository.kt`: implements `CompletionRepository` holding a `RoomCompletionRepository` delegate, an `Outbox`, an `AccountScope` and the `MizanDatabase`. `record` and `undoLast` call the delegate inside `withTransaction` and, when the outcome is `Recorded`/`Reversed` **and** the scope is signed in, enqueue the completion entry (and the day-record entry for `record`) in the same transaction. Every read delegates unchanged. **No import from `RemoteDataSource.kt` or Ktor — this class has no network dependency at all.** Re-run T073 and T074.
- [X] T081 [US2] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/SyncingDayPlanRepository.kt` in the same shape: `ensurePlanFor` delegates and, on `EnsureOutcome.Created` while signed in, enqueues the day-record entry in the same transaction. Every read delegates unchanged.
- [X] T082 [US2] Rebind in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`: keep the Room implementations registered by concrete type, bind `CompletionRepository` and `DayPlanRepository` to the two decorators. Nothing else changes — every existing use case keeps working through the same interfaces.
- [X] T083 [US2] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncWorker.kt` with exactly this constructor: `class SyncWorker(context: Context, params: WorkerParameters, private val engine: SyncEngine, private val catalogue: CataloguePublicationRepository) : CoroutineWorker(context, params)`. `doWork()` calls `engine.drain()` then `engine.pull()` (leave `pull` a no-op until T104), returning `Result.retry()` on `Unreachable` and `Result.success()` otherwise.
- [X] T084 [US2] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncScheduler.kt`: `fun schedule()` enqueues unique expedited work named `"mizan-sync"` with `Constraints(requiredNetworkType = CONNECTED)` and WorkManager's exponential backoff, replacing any existing request. Call it from `OutboxSyncRepository.syncNow()` and from `Outbox.enqueue`.
- [X] T085 [US2] Wire WorkManager into `app/src/main/java/com/giraffe/mizanapp/MizanApplication.kt`: add `workManagerFactory()` to the Koin start block, register `worker { SyncWorker(androidContext(), get(), get(), get()) }` in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`, and call `SyncScheduler.schedule()` once on start-up. Re-run T077.
- [X] T086 [US2] Complete the session-expiry path in `SyncEngine.drain` in `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt`: on `RemoteResult.NotAuthenticated`, end the Supabase session and **leave `AccountScope`, every record and the whole outbox untouched** (FR-006). Re-run T076.
- [X] T087 [US2] Create `app/src/main/java/com/giraffe/mizanapp/sync/SyncStatusBar.kt` — a stateless composable taking `SyncStatus`, rendering the table in [contracts/ui-state.md](./contracts/ui-state.md). Re-run T078 and T079.
- [X] T088 [US2] Host `SyncStatusBar` on `app/src/main/java/com/giraffe/mizanapp/today/TodayScreen.kt` and `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`, fed from `SyncRepository.observeStatus()`. It must not shift, cover, or disable any existing control — re-run `TodayScreenStreakTest` and `WeekScreenTest` to prove nothing regressed.
- [ ] T089 [US2] Run all four suites, then quickstart §4 SC-003 by hand: airplane mode, record a full day, close the app, restore connectivity, wait 60 s, confirm the rows arrived without opening the app.

**Checkpoint**: recording is unchanged for the user, and everything they record reaches the account on its own.

---

## Phase 5: User Story 3 — Two devices show the same record (Priority: P3)

**Goal**: a second device sees the same history; recording on either shows up on the other; a fresh
device is usable in seconds while older history arrives in the background.

**Independent Test**: sign one account in on two devices, record different tasks on each while both
are offline, bring both online, and verify both converge to the same completions and the same earned
scores for every affected day.

### Tests for User Story 3 ⚠️ WRITE AND COMMIT THESE FIRST

- [X] T090 [P] [US3] Add `NOT_YET_KNOWN` to `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/DayCellState.kt` with the KDoc from [data-model.md](./data-model.md) §1, and add a `coverage: RecordCoverage` parameter to `buildDayCells` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt` **with the body still ignoring it**. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/BuildDayCellsCoverageTest.kt`: with complete coverage every existing expectation is unchanged; with incomplete coverage a date before `knownFrom` is `NOT_YET_KNOWN`, not `NOTHING_RECORDED` and not `OUTSIDE_RECORD`; a date at or after `knownFrom` is unaffected; a future date is still `NOT_YET_ELAPSED`. Run it — MUST fail. Add the missing branch to every now-broken `when` and confirm `BuildWeekSummaryTest` still passes.
- [X] T091 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetWeekSummaryCoverageTest.kt`: with incomplete coverage, `GetWeekSummary` does **not** call `ensurePlanFor` for any date below `knownFrom` (assert on a counting fake) and those dates come back `NOT_YET_KNOWN`; with complete coverage the backfill behaviour is byte-for-byte what it is today. Run it — MUST fail.
- [X] T092 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetHistoryPageCoverageTest.kt` (FR-023b names history explicitly): a page covering dates below `knownFrom` reports them as `NOT_YET_KNOWN`, never as 0% and never as absent; paging past the coverage floor does not fabricate empty weeks. Run it — MUST fail.
- [X] T093 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetInsightsCoverageTest.kt` covering `GetMonthOverview`, `GetSectionBreakdown` and `GetPersonalBests` in one parameterised suite: unfetched dates are `NOT_YET_KNOWN`, and each result is marked `provisional` while coverage over its range is incomplete (FR-023d). Run it — MUST fail.
- [X] T094 [P] [US3] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummaryCoverageTest.kt` (FR-023d): while coverage is incomplete over the streak's range the result is provisional; once complete it is not. Run it — MUST fail.
- [X] T095 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RecordCoverageRepositoryTest.kt`: signed out returns `completeFrom(earliestPlanDate())`; signed in with `backfill_complete` set returns the same; signed in mid-backfill returns `RecordCoverage(backfill_floor, complete = false)`; the floor never advances forwards within a session. Run it — MUST fail.
- [X] T096 [P] [US3] Write `app/src/test/java/com/giraffe/mizanapp/ui/DayCellColorsTest.kt`: every `DayCellState` maps to a colour; `NOT_YET_KNOWN`'s colour differs from both `NOTHING_RECORDED`'s and `OUTSIDE_RECORD`'s; **no colour in the file has a red, orange or amber hue** (assert on the channel values). Run it — MUST fail.
- [X] T097 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/TwoDeviceConvergenceTest.kt` (FR-020, SC-004): two in-memory `MizanDatabase` instances sharing one `FakeRemoteDataSource`. Cover concurrent recording on the same day; concurrent undo; one device undoing what the other still shows; and independent first-open of the same date under the **same** catalogue version — after both drain and pull, both databases must report identical completions, identical earned points and identical available totals. Then cover independent first-open under **different** versions and assert the documented outcome (FR-024a/b, SC-004): **neither device's stored day changed at all**, both report the same completions and the same earned points, the fake holds the lower version, and a third database joining fresh derives that lower version. Run it — MUST fail.
- [X] T098 [P] [US3] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BackfillResumeTest.kt` (FR-023a–c, SC-006): pre-load the fake with 400 days; run the head pull and assert today and the current week are present; run the backfill interrupting after pages 1, 3 and 4; after each interruption re-run and assert no record was re-fetched (use the fake's read counter), no record was duplicated, and `backfill_floor` moved monotonically backwards; when finished assert `backfill_complete` is true and all 400 days are present. Run it — MUST fail.

### Implementation for User Story 3

- [X] T099 [US3] Implement the coverage branch in `buildDayCells` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/BuildWeekSummary.kt`: a date for which `coverage.isKnown(date)` is false becomes `NOT_YET_KNOWN`, evaluated **before** the empty/partial/full decision. Re-run T090.
- [X] T100 [US3] Add a `RecordCoverageRepository` constructor parameter to `GetWeekSummary`, `GetHistoryPage`, `GetStreakSummary`, `GetMonthOverview`, `GetSectionBreakdown` and `GetPersonalBests` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/`. Each reads coverage once per invocation and passes it to `buildDayCells`. `GetWeekSummary` additionally skips `ensurePlanFor` for any date where `isKnown` is false. Re-run T091 and T092.
- [X] T101 [US3] Add a `provisional: Boolean` field to the results of `GetStreakSummary`, `GetSectionBreakdown` and `GetPersonalBests`, set when coverage over their range is incomplete (FR-023d). Re-run T093 and T094.
- [ ] T102 [US3] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RoomRecordCoverageRepository.kt` implementing `RecordCoverageRepository`: signed out, or `backfill_complete` set, return `RecordCoverage.completeFrom(earliestPlanDate())`; otherwise `RecordCoverage(knownFrom = backfill_floor, complete = false)`. Read both cursors through `SyncCursorDao`. Re-run T095.
- [ ] T103 [US3] Add `suspend fun applyRemote(changes: RemoteChanges)` to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt`, implementing [contracts/sync-engine.md](./contracts/sync-engine.md) §6 in one transaction. **Day records have exactly two outcomes: if a local plan for that date already exists, do nothing at all** — no re-derivation, no version change, no recalculated total (FR-024a, Conventions §4) — **otherwise** build the plan with `buildDayPlan` at the incoming version, or leave the date `NOT_YET_KNOWN` and request a catalogue pull when that version is not held locally. Completions use `mergeCompletion`, binding `dayPlanId` from `creditedDate`, and never rewrite `pointsAwarded`.
- [ ] T104 [US3] Add `suspend fun pull()` to `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncEngine.kt` per [contracts/sync-engine.md](./contracts/sync-engine.md) §3: `changedSince(cursor, 500)`, apply only rows at or after `backfill_floor`, advance `pull_cursor` to the batch watermark. Wire it into `SyncWorker` in place of the T083 no-op.
- [ ] T105 [US3] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/Backfill.kt` per [contracts/sync-engine.md](./contracts/sync-engine.md) §4: the head pull first, then descending 90-day pages, each applied in one transaction followed by a `backfill_floor` commit, ending at `earliestRecordedDate()` with `backfill_complete` set. Chain the next page from `SyncWorker`. Re-run T097 and T098.
- [ ] T106 [US3] Add a neutral container colour for `NOT_YET_KNOWN` to `app/src/main/java/com/giraffe/mizanapp/ui/DayCellColors.kt`, visually distinct from `NOTHING_RECORDED` and `OUTSIDE_RECORD`. No red, orange or amber value may enter this file. Re-run T096.
- [ ] T107 [US3] Render the loading state in `app/src/main/java/com/giraffe/mizanapp/history/HistoryScreen.kt` and `app/src/main/java/com/giraffe/mizanapp/insights/InsightsScreen.kt`: a `NOT_YET_KNOWN` date shows as still loading, **never as 0%, never as untouched, never as absent** (FR-023b); a provisional figure is labelled still-loading rather than final (FR-023d). Add a case each to `HistoryScreenTest` and `InsightsScreenTest`.
- [ ] T108 [US3] Register `RoomRecordCoverageRepository` in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt` and add the new constructor argument to the six use-case factories in `domainModule`.
- [ ] T109 [US3] Run all four suites, then quickstart §4 SC-004 and SC-006 by hand on two emulators — including the documented different-version exception.

**Checkpoint**: two devices converge, and a fresh device is usable immediately while history arrives.

---

## Phase 6: User Story 4 — Catalogue comes from the account, history stays honest (Priority: P4)

**Goal**: the catalogue is published centrally; a device picks up a newer version, applies it to days
going forward, and leaves every recorded day reporting exactly what it always reported.

**Independent Test**: record history under version N, publish N+1 with changed points and a changed
schedule, sync, and verify every past day still reports N's figures while the current and future days
follow N+1.

### Tests for User Story 4 ⚠️ WRITE AND COMMIT THESE FIRST

- [ ] T110 [P] [US4] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RemoteCatalogueImmutabilityTest.kt` — **the Principle III test this increment owes.** Record 14 days under version 1 and capture every day's tasks, per-task points and available total. Publish version 2 in the fake with different points, a different schedule and a different task set, effective tomorrow. Pull. Assert: all 14 days report identical tasks, identical points and identical available totals; a day opened tomorrow follows version 2; version 1's `task_versions` rows are byte-identical to before the pull. Run it — MUST fail.
- [ ] T111 [P] [US4] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/UnknownCatalogueVersionTest.kt`: (a) FR-028 — publish `format_version = 99`; it is skipped, no exception escapes, the last understood version stays in force, no recorded day changed; (b) FR-027 — publish a payload containing `"editable": true`; it is rejected wholesale with `PullOutcome.Rejected` and nothing is written; (c) a malformed payload behaves the same; (d) **FR-025 — with `unreachable = true` the pull returns `PullOutcome.Unreachable`, the built-in seeded catalogue stays in force, and the app still opens today's plan normally.** Run it — MUST fail.
- [ ] T112 [P] [US4] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/CataloguePullFailureIsolationTest.kt`: with the catalogue pull failing (unreachable, then rejected) but the record endpoints healthy, assert a queued completion still drains and `syncedAt` is still set — a catalogue problem must never block a user's record from being backed up. Run it — MUST fail.

### Implementation for User Story 4

- [ ] T113 [US4] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/RemoteCataloguePublicationRepository.kt` implementing `CataloguePublicationRepository`: call `RemoteDataSource.catalogues(knownFormatVersions = setOf(1))`, skip every publication whose `format_version` is not in that set, run each remaining payload through `scanForAuthoringAffordances` → `parseCatalogue` → `CatalogueValidator` **before writing anything**, and insert only versions absent from `catalogue_versions`. **No update and no delete path may exist in this class** — a version already present is skipped, never overwritten (research R10). `Unreachable` and `Rejected` both leave the built-in seed in force. Re-run T110 and T111.
- [ ] T114 [US4] Call `pullIfNewer()` from `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/SyncWorker.kt` before the record pull, wrapped so that any non-`Added` outcome is logged and ignored rather than short-circuiting `drain()` or `pull()`. Register the repository in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`. Re-run T112.
- [ ] T115 [US4] Run all four suites, then quickstart §4 SC-009 by hand against the real project.

**Checkpoint**: the catalogue comes from the account and history is provably untouched by it.

---

## Phase 7: User Story 5 — The app still belongs to someone who never signs in (Priority: P5)

**Goal**: no account, no problem — and a signed-in user can sign out either way and keep using it.

**Independent Test**: on a fresh install with no account and no network, run the complete Phase 2–6
flow — today, week, streak, history, insights — and verify nothing is blocked, hidden or degraded,
and that no screen prompts or nags for an account.

### Tests for User Story 5 ⚠️ WRITE AND COMMIT THESE FIRST

- [ ] T116 [P] [US5] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/SignOut.kt` and `.../UpdateDisplayName.kt` with `TODO("T123")` bodies. Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/SignOutTest.kt`: both modes report the pending count before acting; `KEEP_LOCAL_RECORDS` never reaches the wipe; `REMOVE_LOCAL_RECORDS` reaches it only after confirmation; **neither mode ever calls anything that removes a remote record** (use a fake that fails the test if it does). Run it — MUST fail.
- [ ] T117 [P] [US5] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SignOutModesTest.kt`: after `KEEP_LOCAL_RECORDS`, every plan, planned task and completion is present with identical figures and the app can still record; after `REMOVE_LOCAL_RECORDS`, those tables are empty, `outbox`, `sync_cursors` and `account_scope` are cleared, **the catalogue tables are untouched**, and the fake remote still holds everything it had accepted. Then sign the same account back in and assert the full record returns with no duplication (US5 AS5). Run it — MUST fail.
- [ ] T118 [P] [US5] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ForeignAccountIsolationTest.kt` (FR-013, FR-013a, US5 AS6): sign in as user A, record, sign out plainly; call `confirmCode` for user B with `replaceLocalRecords = false` and assert it returns `WouldReplaceLocalRecords` carrying A's email, the recorded-day count, the completion count and the unsynced count — **and that no session was opened and nothing changed**; then call with `replaceLocalRecords = true` and assert A's records are gone locally, none was ever attributed to B, and B's account received nothing belonging to A. Run it — MUST fail.
- [ ] T119 [P] [US5] Write `app/src/test/java/com/giraffe/mizanapp/profile/ProfileViewModelTest.kt`: display name saves, clears, and falls back to the email when empty; both sign-out paths surface a confirmation; the removing path's confirmation names the day count and the completion count; both warn when the pending count is non-zero; **the conflict-policy statement required by FR-019a is present in the state**; no string contains a forbidden word. Run it — MUST fail.
- [ ] T120 [P] [US5] Write `app/src/androidTest/java/com/giraffe/mizanapp/profile/ProfileScreenTest.kt`: the removing confirmation names what will be removed before it can proceed; the account-switch confirmation names the account being replaced; the conflict-policy line is visible on the screen (FR-019a). Run it — MUST fail.
- [ ] T121 [P] [US5] Write `app/src/androidTest/java/com/giraffe/mizanapp/NoAccountGateTest.kt` (FR-004, SC-007): with no session configured, navigate Today → Week → History → Insights → a past day and back; assert every screen renders its normal content, no account prompt, dialog, banner or interstitial appears anywhere, and `SyncStatusBar` renders nothing. Run it — MUST fail.
- [ ] T122 [P] [US5] Extend `app/src/test/java/com/giraffe/mizanapp/NavigationRoutingTest.kt` with a `PROFILE` case, mirroring T059's `SIGNIN` case. Run it — MUST fail.

### Implementation for User Story 5

- [ ] T123 [US5] Implement `SignOut` and `UpdateDisplayName` in `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/`. `SignOut(accounts, sync)` reads `observePendingCount()` first and returns it with the outcome so the caller can warn (FR-007c). Re-run T116.
- [ ] T124 [US5] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/LocalRecordWipe.kt` with a single `suspend fun wipe()` clearing `day_plans`, `planned_tasks`, `completions`, `outbox`, `sync_cursors` and `account_scope` in **one** transaction. It must not touch `sections`, `task_definitions`, `catalogue_versions` or `task_versions` — the app has to be usable the moment it returns. It must issue no remote call of any kind (FR-007d). KDoc naming its only two callers.
- [ ] T125 [US5] Complete `signOut(REMOVE_LOCAL_RECORDS)` in `data/src/main/kotlin/com/giraffe/mizanapp/data/repository/SupabaseAccountRepository.kt` (the `TODO("T125")` from T062): end the session, then call `LocalRecordWipe.wipe()`. Re-run T117.
- [ ] T126 [US5] Complete the `replaceLocalRecords` branch in `SupabaseAccountRepository.confirmCode` (the `TODO("T126")` from T062): when `AccountScope` holds a different `userId` and `replaceLocalRecords` is false, return `WouldReplaceLocalRecords(currentEmail, recordedDays, completionCount, unsyncedCount)` **without opening a session or changing anything**; when true, wipe and then sign in. Re-run T118.
- [ ] T127 [US5] Create `app/src/main/java/com/giraffe/mizanapp/profile/ProfileUiState.kt`, `ProfileViewModel.kt` and `ProfileScreen.kt` per [contracts/ui-state.md](./contracts/ui-state.md), including the plain-language conflict-policy line (FR-019a). The removing confirmation names the day count and the completion count before it can be accepted. Re-run T119 and T120.
- [ ] T128 [US5] Add `Destination.Profile` to `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` (`"PROFILE"` in `encode`/`decode`, a `ProfileRoute`, an `AppRoute` branch), make the Today entry point from T070 open `Profile` when signed in and `SignIn` when signed out, and register the ViewModel in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`. Re-run T121 and T122.
- [ ] T129 [US5] Run all four suites plus quickstart §4 SC-007 by hand: fresh install, airplane mode, no account, walk the whole Phase 2–6 product.

**Checkpoint**: every user story is complete and the signed-out product is provably unchanged.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T130 Re-run the row-level-security verification as the merge gate (SC-008): `supabase db execute --file specs\007-identity-cloud-sync\contracts\rls-verification.sql`. Must print `RLS OK`. Confirm `supabase/migrations/0001_identity_cloud_sync.sql` is still byte-identical to `specs/007-identity-cloud-sync/contracts/remote-schema.sql`.
- [ ] T131 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SupabaseRemoteDataSourceContractTest.kt`: a thin live test, skipped with `Assume.assumeTrue` when `BuildConfig.SUPABASE_URL` is blank. Assert one upsert round-trips, a duplicate upsert leaves one row, a tombstone upsert cannot be cleared, and a day-record upsert with a higher version does not raise the stored version.
- [ ] T132 [P] Audit every string added by this increment against Conventions §6 and the `CLAUDE.md` Principle IX list (SC-011): both sign-out confirmations, the account-switch confirmation, every `SyncStatus` string, every sign-in step, the still-loading labels, and the conflict-policy line. Record the audit as a comment block at the top of `app/src/test/java/com/giraffe/mizanapp/sync/SyncStatusCopyTest.kt`.
- [ ] T133 [P] Boundary and prohibition audit. `Grep` for `io.github.jan`, `io.ktor`, `androidx.work` and `org.koin` across `domain/src` — must return nothing; across `data/src` — must appear only in `sync/SupabaseClientFactory.kt`, `sync/SupabaseRemoteDataSource.kt`, `sync/SyncWorker.kt` and `sync/SyncScheduler.kt`. Then `git grep -n "adoptMergedVersion\|updateMergedVersion"` — must return nothing (FR-024a), and `git grep -n "signInWith(Email"` — must return nothing (FR-002). Confirm `ModuleBoundaryTest` passes.
- [ ] T134 [P] Confirm `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/3.json` is committed and that `1.json` and `2.json` are byte-identical to `develop-v1` (`git diff develop-v1 -- data/schemas`).
- [ ] T135 [P] Update `docs/PLAN.md` to mark Phase 7 as delivered, and record the conflict policy there for developers. Note that FR-019a's user-facing obligation is satisfied by the profile screen's statement (T127), not by this file.
- [ ] T136 Run the full quickstart §4 validation end to end, all eleven success criteria, and record the result in the pull request description.
- [ ] T137 Pre-merge gate check against `CLAUDE.md`: Constitution Check passes and names each principle touched; all four suites green; **the PR's commit history shows every test task committed before its implementation task** (Principle I — verify with `git log --oneline` on the branch); the Principle III historical-immutability test (T110) is present and passing; the Room migration is additive and its schema exported.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup, T001–T009)**: no dependencies. **T007–T008 must complete before US1** — the remote schema has to exist and be proven isolated before a real sign-in.
- **Phase 2 (Foundational, T010–T051)**: needs Phase 1. **Blocks every user story.**
- **Phase 3 (US1, T052–T072)**: needs Phase 2.
- **Phase 4 (US2, T073–T089)**: needs Phase 2 and `SyncEngine.drain` (T065).
- **Phase 5 (US3, T090–T109)**: needs Phase 2 and T065.
- **Phase 6 (US4, T110–T115)**: needs Phase 2 and `SyncEngine.pull` (T104).
- **Phase 7 (US5, T116–T129)**: needs Phase 2 and `SupabaseAccountRepository` (T062).
- **Phase 8 (Polish, T130–T137)**: needs every story being shipped.

### Hard ordering inside Phase 2

`T030 → T031 → T032/T033/T034 → T035 → T036 → T037 → T038 → T039 → T040`. Entities and DAOs must
exist before `MizanDatabase` declares them, and the database must be at version 3 with `3.json`
exported before the migration test can run at all.

### Within a user story

Test task → implementation task, always, with a commit between them. Domain before data before UI.

### Parallel opportunities

- T004, T005, T006 in Phase 1.
- T010–T014 (types) all in parallel; then the stub/test pairs T015/T017/T019/T021 in parallel; then T025–T028 in parallel.
- T032, T033, T034 (three new DAOs) in parallel; T041, T042, T043 in parallel.
- Every test task inside a single story phase is `[P]` — they touch different files.
- With more than one developer, US2, US3 and US5 can proceed in parallel once Phase 2 and T062/T065 exist.

---

## Parallel Example: User Story 1

```text
# Write these nine test files together — different files, no shared state:
T052  ConfirmSignInCodeTest      (domain, JVM)      T057  SignInViewModelTest      (app, JVM)
T053  SignInMigrationTest        (data, device)     T058  SignInScreenTest         (app, device)
T054  SignInMigrationResumeTest  (data, device)     T059  NavigationRoutingTest    (app, JVM)
T055  SignInUnionTest            (data, device)     T060  SyncStatusRepositoryTest (data, device)
T056  SessionPersistenceTest     (data, device)

# Then implement in order — these share SyncEngine.kt and must be sequential:
T063 -> T064 -> T065 -> T066
```

---

## Implementation Strategy

### MVP first (User Story 1 only)

1. Phase 1 Setup → Phase 2 Foundational.
2. Phase 3 US1.
3. **Stop and validate**: quickstart §4 SC-001 by hand, including the uninstall/reinstall round trip.
4. Shippable on its own: a user's history is backed up.

### Incremental delivery

US1 → US2 → US3 → US4 → US5, validating each against its Independent Test before starting the next.

### Highest-risk tasks — slow down on these

| Task | Risk |
|---|---|
| T039 | A destructive migration cannot be undone on a user's device. Additive only: no DROP, no RENAME, no UPDATE. |
| T066 | Migration order is what makes an interruption recoverable. Do not reorder the four steps. |
| T080 | If the outbox insert leaves the record's transaction, a completion can exist locally and never sync. Same transaction, always. |
| T103 | The ingest path. A day that already exists locally must be left **completely alone** — no re-derivation, no version change, no recalculated total (FR-024a). |
| T113 | An update or delete path here would re-score recorded history. There must be neither. |
| T124 | The only destructive code in the product. Two confirmations before it runs; catalogue tables untouched; no remote call. |
| T126 | Opens no session and changes nothing on the first call. A session opened before the confirmation would destroy another account's records without consent. |

---

## Notes

- `[P]` means different files and no dependency on an incomplete task.
- Commit after every task. Never squash a test task into its implementation task — the PR's commit ordering is the Principle I evidence and it is checked at merge (T137).
- If a test passes the first time you run it, the test is wrong. Fix the test before implementing.
- If a task seems to require editing a file listed in Conventions §3, or creating a method Conventions §4 forbids, stop and re-read the task.
