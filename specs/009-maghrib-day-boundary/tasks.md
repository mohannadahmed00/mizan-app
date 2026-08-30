# Tasks: Maghrib-Anchored Day and Week Boundary

**Input**: Design documents from `/specs/009-maghrib-day-boundary/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: MANDATORY, not optional. Constitution Principle I is non-negotiable: no production code
may be written before a failing test that requires it. Every `[TEST]` task below MUST be written,
run, and observed failing **for the right reason** before its matching implementation task.

---

## READ THIS BEFORE STARTING ANY TASK

You are implementing one increment of an existing Android app. Follow these rules exactly.

### Absolute rules — breaking any of these fails the increment

1. **Test first, always.** For every pair, do the `[TEST]` task, run it, watch it fail, then do the
   implementation task. Commit the test and the implementation as **separate commits, test first**.
   The pull request's commit order is the only evidence that this happened, and it is checked before
   merge.
2. **Never modify these files**: anything under `data/schemas/*/1.json`, `2.json`, `3.json`,
   `4.json`, `domain/src/main/resources/catalogue/valid-catalogue.json`, the existing `MIGRATION_1_2`
   and `MIGRATION_2_3` blocks in
   `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Migrations.kt`, or the `MIGRATION_3_4` block in
   `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`. The migrations are **split
   across those two files** — check both before assuming where one lives. T024a *registers*
   `MIGRATION_3_4`, which is a change to `MizanDatabaseFactory.kt` and not to the migration block.
3. **Never write a destructive migration.** No `DROP`, no `RENAME`, no `UPDATE`, no `DELETE` in any
   migration. Only `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE ... ADD COLUMN`.
4. **Never add a dependency to `domain/build.gradle.kts`.** That module must stay pure Kotlin. If
   you find yourself wanting `kotlinx-datetime`, Room, Android, or Adhan there, you are in the
   wrong module.
5. **Never call `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()`, or
   `ZoneId.systemDefault()`** anywhere except `data/src/main/kotlin/com/giraffe/mizanapp/data/time/SystemTimeProvider.kt`.
   `BoundaryStateStore` is **not** an exception: it takes the current instant and zone as parameters
   to `refresh(now, zone)`. `SystemTimeProvider` reads the store's resolved state, so a store that
   read the clock itself would close a cycle and give Principle VII the second clock read it forbids.
6. **Never add a network call.** No Retrofit, no Ktor, no `Geocoder`, no HTTP of any kind in this
   feature.
7. **Never write red colours, ✗ marks, warning icons, or failure/guilt wording** into any UI string
   or component added here. See `CLAUDE.md` "Design".
8. **Do not touch any remote/Supabase artifact.** No SQL, no edge function, no RLS change.

### Definition of "run the tests"

```bash
./gradlew :domain:test                 # fast, no emulator
./gradlew :app:test                    # fast, no emulator
./gradlew :data:connectedAndroidTest   # needs a running emulator/device
./gradlew :app:connectedAndroidTest    # needs a running emulator/device
```

### If a task's premise turns out to be wrong

Stop and report it. Do not improvise a different design. Every decision here was made in
[research.md](./research.md) with alternatives recorded.

---

## Phase 1: Setup

**Purpose**: dependency and seed content. No behaviour changes yet.

- [X] T001 Add the Adhan version and library to `gradle/libs.versions.toml`: under `[versions]` add
      `adhan = "1.2.1"`, and under `[libraries]` add
      `adhan = { group = "com.batoulapps.adhan", name = "adhan", version.ref = "adhan" }`. Add a
      comment above the version line saying it is spec 009's prayer-time calculation, MIT licensed,
      and confined to `:data`. **This is the Java port, not `adhan2`** — see T002 and research R5 for
      why the Kotlin rewrite was rejected.

- [X] T002 Add `implementation(libs.adhan)` to the `dependencies` block of `data/build.gradle.kts`.
      Do **not** add it to `app/build.gradle.kts` or `domain/build.gradle.kts`.

      **Resolved 2026-08-30 by taking the fallback this task and research R5 had already recorded.**
      `adhan2:0.0.7` was tried first and broke `:data` outright: it is compiled against Kotlin 2.4.0
      and pulls `kotlin-stdlib:2.4.0` onto the compile classpath, whose metadata this project's Kotlin
      2.2.10 cannot read (it reads up to 2.3.0), so every stdlib symbol in the KSP-generated
      `MizanDatabase_Impl.kt` failed to resolve —
      `Module was compiled with an incompatible version of Kotlin. The binary version of its metadata
      is 2.4.0, expected version is 2.2.0`. The Java port carries no Kotlin metadata and cannot
      conflict. `./gradlew :data:compileDebugKotlin` is green with it, and the switch cost nothing
      because `AdhanPrayerTimes` (T027) had not been written yet.
      Do **not** add it to `app/build.gradle.kts` or `domain/build.gradle.kts`. Run
      `./gradlew :data:compileDebugKotlin` to confirm it resolves. If the build fails on a
      `kotlinx-datetime` version conflict, switch to the pure-Java fallback recorded in research R5:
      use `com.batoulapps.adhan:adhan:1.2.1` with artifact name `adhan` instead, and note the switch
      in a comment. Do not attempt any other fix.

- [X] T003 [P] Add `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`
      to `app/src/main/AndroidManifest.xml`, above the `<application>` tag. Do **not** add
      `ACCESS_FINE_LOCATION` or `ACCESS_BACKGROUND_LOCATION` — FR-005 permits coarse only.

- [X] T004 [P] Create the seed file
      `domain/src/main/resources/prayer/region-conventions.json` with exactly the shape documented in
      [contracts/region-conventions.md](./contracts/region-conventions.md). Populate `version: 1`, a
      `default` of `MUSLIM_WORLD_LEAGUE` / `STANDARD`, and two region entries: `Africa/Cairo` →
      `EGYPTIAN` / `STANDARD`, and `Asia/Riyadh` → `UMM_AL_QURA` / `STANDARD`. Add no other regions —
      Principle VIII forbids entries for regions no one is in yet.

**Checkpoint**: project builds. `./gradlew build` passes. No behaviour has changed.

---

## Phase 2: Foundational (BLOCKING — no user story can start until this phase is done)

**Purpose**: the domain types, the changed boundary rule, the storage, and the provider. Everything
in Phase 3+ depends on all of it.

### 2a. Pure domain types (no tests needed — these are data declarations only)

- [X] T005 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/Coordinates.kt`.
      `data class Coordinates(val latitude: Double, val longitude: Double)` with an `init` block
      requiring latitude in −90..90 and longitude in −180..180, each with a message naming the bad
      value. No other fields — no accuracy, no timestamp, no provider (FR-006).

- [X] T006 [P] **Reopened — the two enums may already exist; `SelectedConvention` is the missing
      piece.** Create or extend
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/CalculationConvention.kt`
      containing `enum class CalculationConvention { MUSLIM_WORLD_LEAGUE, UMM_AL_QURA, EGYPTIAN, ISNA, KARACHI }`,
      `enum class AsrMadhab { STANDARD, HANAFI }`, and
      `data class SelectedConvention(val convention: CalculationConvention, val asr: AsrMadhab)`.
      `SelectedConvention` is what `conventionFor` returns (T017) and what the mapping's `default`
      entry deserialises to, so the matched and unmatched paths return the identical type and no
      caller can assemble a half-default.

- [X] T007 [P] Create
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/PrayerTimesProvider.kt` containing
      the `PrayerTimes` data class, the `PrayerTimesOutcome` sealed interface with its three cases,
      and the `PrayerTimesProvider` interface — all exactly as written in
      [contracts/prayer-times-provider.md](./contracts/prayer-times-provider.md). Use `java.time`
      types. Add a KDoc saying this is the single provider constitution Principle VII requires and
      that spec 010 consumes it rather than adding a second.

- [X] T008 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/LocationSource.kt`
      with the `LocationSource` interface from the same contract. KDoc: the only location reader in
      the app; returns null rather than throwing when no fix is available.

- [X] T009 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/BoundaryRegime.kt`:
      a sealed interface `BoundaryRegime` with `data object Maghrib` and
      `data class Fallback(val reason: FallbackReason)`, plus
      `enum class FallbackReason { NEVER_HAD_LOCATION, ERASED, ZONE_CHANGED_AWAITING_FIX }`. These
      are the only three routes to the fallback — do not add a fourth.

- [X] T010 [P] **Reopened — `lastResolvedRegime` was missing.** Create
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/BoundaryState.kt` as a
      data class with exactly the fields in [data-model.md](./data-model.md) §1 `BoundaryState`:
      `regime`, `coordinates`, `zoneIdWhenObtained`, `resolvedDate`, `expiresAt`, `lastResolvedDate`,
      `lastResolvedRegime`. `resolvedDate` and `expiresAt` are non-null. Add an `init` requiring that
      `coordinates` is non-null when `regime` is `Maghrib` and null when it is `Fallback`.
      `lastResolvedRegime` is what tells the clamp whether a resolution is a seam (FR-023a); it is
      persisted rather than kept in memory alone, because the changeover is nearly always the first
      launch after an update and an in-memory comparison would miss exactly that case.

- [X] T010a [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/BoundaryStatus.kt`
      with the interface exactly as in
      [contracts/boundary-provider.md](./contracts/boundary-provider.md): `current(): BoundaryState`,
      `observe(): Flow<BoundaryState>`, `suspend fun refresh(now: Instant, zone: ZoneId)`,
      `suspend fun requestLocation(): LocationRequestOutcome`, `suspend fun eraseLocation()`, plus the
      `LocationRequestOutcome` sealed interface with its three cases (`Obtained`, `PermissionDenied`,
      `NoFixAvailable`). KDoc: this is the app-facing authority on which boundary rule is in force,
      and the only surface the FR-016, FR-012d, FR-017b and FR-017c disclosure reads from — T035,
      T051, T055 and T066 all reach the boundary through it. `:domain` already has
      `kotlinx-coroutines-core`, so `Flow` needs no build change (rule 4 still holds).

### 2b. The changed day boundary rule — TEST FIRST

- [X] T011 [TEST] Rewrite `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/DayBoundaryTest.kt`
      for the new three-argument signature
      `dateAt(instant: Instant, zone: ZoneId, maghribOnCivilDate: Instant?): LocalDate`. Write these
      test methods, all using `ZoneId.of("Africa/Cairo")`:
      `instantBeforeMaghribBelongsToTheCivilDate`,
      `instantExactlyAtMaghribBelongsToTheNextDate`,
      `instantAfterMaghribBelongsToTheNextDate`,
      `instantAfterMidnightButBeforeMaghribStillBelongsToTheCivilDate`,
      `nullMaghribFallsBackToTheCivilDate`,
      `midnightDoesNotAdvanceTheDateWhenMaghribIsSupplied`.
      Pick a fixed date (e.g. 2026-03-13, a Friday) and a Maghrib of 18:00 local. Run
      `./gradlew :domain:test` and confirm it fails to compile — that is the correct first failure.

- [X] T012 Change `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/DayBoundary.kt` to the new
      signature. Implementation: compute `civilDate = instant.atZone(zone).toLocalDate()`; if
      `maghribOnCivilDate` is null return `civilDate`; otherwise return
      `if (!instant.isBefore(maghribOnCivilDate)) civilDate.plusDays(1) else civilDate`. Update the
      KDoc to describe the Maghrib rule and to keep the existing statement that this rule exists here
      and nowhere else.

      **In the same commit, update the one production caller** —
      `data/src/main/kotlin/com/giraffe/mizanapp/data/time/SystemTimeProvider.kt`, whose `today()`
      currently reads `DayBoundary.dateAt(now(), zone())` — to pass `null` as the third argument.
      That is not a compatibility shim: `null` **is** the fallback regime, so this is the honest
      expression of what the app resolves before a boundary state store exists, and it preserves
      today's behaviour exactly. T033 replaces the whole body with a read of the resolved state, at
      which point the literal `null` disappears. Do **not** keep the old two-argument function as an
      overload; a second entry point that silently means "midnight" is precisely the second opinion
      Principle VII forbids, and it would compile every stale caller into permanent staleness.

      A signature and its single production caller are one compile unit, so they move together —
      splitting them would leave `:data` uncompilable between two tasks, and every later checkpoint
      would be unverifiable.

      Verify with `./gradlew :domain:compileKotlin :data:compileDebugKotlin`. **The test source sets
      do not compile yet** — T013 is what makes them compile, so do not run `:domain:test` here.

- [X] T013 Fix every test caller broken by T012's signature change. In each, pass `null` as the third
      argument: these doubles are all on the fallback boundary, and the ones that need a real Maghrib
      get it from their own literals in later phases, never from a production default.

      Five call sites across four files — the first three are `TimeProvider` doubles, the last two are
      direct calls in existing tests, and it is the last two that a grep for `today()` alone will
      miss:

      - `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/FakeTimeProvider.kt` — its
        `override fun today()`.
      - `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DbTestBase.kt` — the `TestTimeProvider`
        class inside it.
      - `app/src/test/java/com/giraffe/mizanapp/today/FakeRepositories.kt` — its `TimeProvider`
        implementation, found by the `override fun today()` it declares, not by line number.
      - `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/RegionalPeriodBoundaryTest.kt`
        — **five** `DayBoundary.dateAt(instant, zone)` calls, one per zone under test.
      - `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/ZoneChangeReassignsRegionTest.kt` — the
        `assertEquals(DayBoundary.dateAt(time.now(), time.zone()), time.today())` assertion.

      Confirm the list is exhaustive before running anything:
      `grep -rn "dateAt(" --include=*.kt domain data app`. Every hit outside `DayBoundary.kt` itself,
      `DayBoundaryTest.kt` and `SystemTimeProvider.kt` must appear above; if the grep finds one that
      does not, fix it the same way and say so.

      Both of those existing tests keep asserting exactly what they asserted before — with `null` the
      rule returns the civil date, which is the behaviour they were written against — so neither needs
      a rewritten expectation. Neither is testing the day boundary; they are testing region
      reassignment and period derivation, and they reach through `dateAt` only to say "the date this
      device is on".

      Run `./gradlew :domain:test :app:test` — everything must compile, T011 must pass, and every
      pre-existing test must still pass.

### 2c. The changeover clamp — TEST FIRST

- [X] T014 [TEST] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/ResolveBoundaryDateTest.kt`. The clamp
      is armed **only** across a regime change, so the tests come in two groups.

      Regime unchanged (`regimeChanged = false`) — the computed date is adopted as-is:
      `firstEverResolutionTakesTheComputedDate` (lastResolved null → computed),
      `withinOneRegimeTheComputedDateIsAdoptedUnchanged` (computed = last+5 → returns last+5; a
      person returning after five days away must get today's date, not one that closed four days
      ago),
      `withinOneRegimeAnEarlierComputedDateIsAdoptedUnchanged` (computed = last−1 → returns last−1; a
      corrected device clock takes effect immediately).

      At a seam (`regimeChanged = true`) — the clamp applies:
      `atASeamTheSameDateResolvesToItself`,
      `atASeamAdvancingOneDayIsAllowed`,
      `atASeamAdvancingTwoDaysIsClampedToOne` (computed = last+2 → returns last+1),
      `atASeamGoingBackwardsIsClampedToTheLastResolvedDate` (computed = last−1 → returns last).

      Run and confirm it fails to compile.

- [X] T015 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/time/ResolveBoundaryDate.kt`
      with
      `fun resolveBoundaryDate(computed: LocalDate, lastResolved: LocalDate?, regimeChanged: Boolean): LocalDate`.
      If `lastResolved` is null, **or `regimeChanged` is false**, return `computed`; otherwise return
      `computed.coerceIn(lastResolved, lastResolved.plusDays(1))`. KDoc: the clamp is armed only
      across a regime change, because that is the only moment two different rules are applied either
      side of one instant and therefore the only moment a date can be skipped or duplicated (FR-022,
      FR-023, research R7). State explicitly that clamping *every* resolution would credit a
      returning person's completions to a day that has already closed and would make the answer
      depend on launch frequency, which FR-023a and FR-017 forbid. Run `./gradlew :domain:test`.

### 2d. Region-to-convention mapping — TEST FIRST

- [X] T016 [TEST] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/prayer/ConventionForRegionTest.kt` with:
      `cairoSelectsTheEgyptianConvention`,
      `riyadhSelectsUmmAlQura`,
      `anUnmappedZoneSelectsTheDocumentedDefault` (use `America/New_York`),
      `theDefaultIsMuslimWorldLeagueWithStandardAsr`.
      Build the mapping in the test from a literal object, not from the resource file.

- [X] T017 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/ConventionForRegion.kt`
      with a `RegionConventionMapping` data class (`version`, `default`, `regions`), a
      `RegionConventionEntry` data class (`zoneIds`, `convention`, `asr`), and
      `fun conventionFor(zoneId: String, mapping: RegionConventionMapping): SelectedConvention`.
      Returns the first entry whose `zoneIds` contains `zoneId`, else `mapping.default`. It must
      never throw and never return null. `SelectedConvention` is the type from T006, and
      `RegionConventionMapping.default` is itself a `SelectedConvention`, so both paths return the
      same shape. Run `./gradlew :domain:test`.

- [X] T018 [TEST] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/prayer/RegionConventionsSeedTest.kt` which
      loads the real `prayer/region-conventions.json` from resources and asserts the five validation
      rules in [contracts/region-conventions.md](./contracts/region-conventions.md): default present
      and valid; every convention and madhab name resolves to an enum constant; **no zone id appears
      in two entries**; every `zoneIds` array is non-empty and every id parses via `ZoneId.of`;
      `version` is positive. The duplicate-zone assertion is the important one — it is the
      "second opinion within a region" the constitution forbids.

- [X] T019 Add the serialization/parsing needed to make T018 pass, in
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/prayer/RegionConventionsJson.kt`. Follow
      the existing pattern in
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/catalogue/CatalogueJson.kt` — same
      `kotlinx.serialization` approach, same resource-loading style. Run `./gradlew :domain:test`.

### 2e. Room storage — TEST FIRST

- [X] T020 [TEST] Create
      `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BoundaryStateMigrationTest.kt` using
      `MigrationTestHelper` (see how spec 008's migration test does it, if one exists, and follow
      that shape). Assert: migrating 4 → 5 succeeds; `boundary_state` exists afterwards; a row
      inserted into `day_plans` and `completions` before the migration is byte-identical after it.
      Run `./gradlew :data:connectedAndroidTest` and confirm it fails.

- [X] T021 Create
      `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/BoundaryStateEntity.kt` with
      `@Entity(tableName = "boundary_state")` and exactly the columns in
      [data-model.md](./data-model.md) §2: `id` (Int, `@PrimaryKey`, always 0), `latitude` (Double?),
      `longitude` (Double?), `zoneIdWhenObtained` (String?), `obtainedAt` (Long?),
      `lastResolvedDate` (String?), `lastResolvedRegime` (String?), and `promptShown` (Boolean,
      non-null, annotated **`@ColumnInfo(defaultValue = "0")`**).

      That annotation is not optional. T023's migration SQL declares `DEFAULT 0`, and without a
      matching `defaultValue` the schema Room exports disagrees with the migrated database, so
      `runMigrationsAndValidate` in T020 fails on a column default mismatch.
      `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entity/ParticipationStateEntity.kt` is the
      existing precedent — note it sits in `entity/` (singular) while this file goes in `entities/`
      (plural). The project has both packages; this feature stays with the plural one the day and
      sync entities use.

      Add a KDoc stating this table is deliberately **not** synchronisable, that Principle V governs
      synchronisable rows only, and that `account_scope` is the existing precedent.

- [X] T022 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/BoundaryStateDao.kt` with
      `suspend fun get(): BoundaryStateEntity?`, `fun observe(): Flow<BoundaryStateEntity?>`, and
      `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: BoundaryStateEntity)`.
      Do **not** add a delete method — erasing coordinates nulls columns in place (data-model §2).
      Put the file in `daos/` (plural), alongside `DayPlanDao`, not the `dao/` package spec 008
      added. Both exist; this feature stays with the older, larger one.

- [X] T023 Add `MIGRATION_4_5` to the **end** of
      `data/src/main/kotlin/com/giraffe/mizanapp/data/db/Migrations.kt`, following the style of
      `MIGRATION_1_2` and `MIGRATION_2_3` there. (`MIGRATION_3_4` is **not** in that file — it lives
      in `MizanDatabase.kt`. Leave it where it is.) It contains one statement:
      `CREATE TABLE IF NOT EXISTS boundary_state (id INTEGER NOT NULL PRIMARY KEY, latitude REAL, longitude REAL, zoneIdWhenObtained TEXT, obtainedAt INTEGER, lastResolvedDate TEXT, lastResolvedRegime TEXT, promptShown INTEGER NOT NULL DEFAULT 0)`.
      Add a KDoc saying it is purely additive, that the absent row is the correct initial state
      meaning "no coordinates ever held", and that nothing is backfilled.

- [X] T024 In `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`: bump
      `version = 4` to `version = 5`, add `BoundaryStateEntity::class` to the `entities` array, and
      add `abstract fun boundaryStateDao(): BoundaryStateDao`. Do not touch the `MIGRATION_3_4` block
      further down the same file.

- [X] T024a Register the migrations in
      `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabaseFactory.kt`. The call currently
      reads `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`; it must read
      `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`, with both new
      names imported.

      **`MIGRATION_3_4` has never been registered.** It is declared in `MizanDatabase.kt` and handed
      to nothing, and Room runs only the migrations passed to `addMigrations` — so an installed
      database at version 3 has no path forward and T023's migration would sit unreachable behind it.
      Fixing it here is what makes this feature's own migration run at all, which is why it is in
      scope rather than deferred. Do **not** add `fallbackToDestructiveMigration` under any
      circumstances; a recorded day must survive every upgrade.

- [X] T025 Run `./gradlew :data:connectedAndroidTest` so Room exports the new schema, then confirm
      `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/5.json` exists and **commit it**. A
      release PR is blocked without it.

### 2f. The providers in `:data`

- [X] T026 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/prayer/AndroidLocationSource.kt`
      implementing `LocationSource` using the platform `android.location.LocationManager` — **not**
      Play Services. `hasPermission()` checks `ACCESS_COARSE_LOCATION` via
      `ContextCompat.checkSelfPermission`. `current()` returns `getLastKnownLocation` from the
      network provider if available; otherwise requests a single fix (`getCurrentLocation` on API 30+,
      a single-shot `requestLocationUpdates` with immediate `removeUpdates` below that) and returns
      null if none arrives. Return only latitude and longitude — discard accuracy, altitude, bearing
      and speed. Never request continuous updates. Never log a coordinate.

- [X] T027 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/prayer/AdhanPrayerTimes.kt`
      implementing the **single-method** `PrayerTimesProvider` of
      [contracts/prayer-times-provider.md](./contracts/prayer-times-provider.md) —
      `timesFor(date, at, zone)` — using `com.batoulapps.adhan` (the Java port; `adhan2` was rejected,
      see R5). This is the **only** file in the app allowed to import it.

      The port works in `java.util.Date` and `DateComponents`, not `kotlinx-datetime`, so convert at
      this boundary: build `DateComponents` from the `LocalDate`, and map each returned `Date` to an
      `Instant` with `.toInstant()`. Nothing outside this file sees a `java.util.Date`.

      Coordinates and zone are always passed in; there is no "use the currently held coordinates"
      overload. The only holder of coordinates is `BoundaryStateStore`, which already depends on this
      provider, so that convenience would close a dependency cycle and give the held-coordinates
      question a second home.

      It selects its convention via `conventionFor(zone.id, mapping)` and maps that to the library's
      calculation-method and madhab parameters. It returns `CalculationFailed` when the library
      cannot produce a Maghrib (this happens above the polar circles) and `Calculated` otherwise. It
      must never throw, never read the device clock or default zone, and never substitute a default
      location.

- [X] T028 [TEST] Create `FakePrayerTimes` and `FakeLocationSource` as described in
      [contracts/prayer-times-provider.md](./contracts/prayer-times-provider.md) — **once in each
      source set that consumes them**:
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/prayer/`,
      `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/prayer/`, and
      `app/src/test/java/com/giraffe/mizanapp/prayer/`.

      They cannot be shared from `:domain`: its test sources are on neither `:data`'s androidTest
      classpath nor `:app`'s test classpath (`data/build.gradle.kts` takes `api(project(":domain"))`,
      which is main only), and this project has no `java-test-fixtures` setup. `DbTestBase`'s own
      `TestTimeProvider` is the existing precedent for duplicating a double rather than adding a
      fixtures module — do not add one here. Keep the copies identical in behaviour; they are the
      substitutability FR-004 requires.

**Checkpoint**: `./gradlew build test` passes. `./gradlew :data:connectedAndroidTest` passes. The app
still behaves exactly as before, because nothing yet feeds a real Maghrib into `DayBoundary`.

---

## Phase 3: User Story 1 — The day turns over at Maghrib (P1)

**Goal**: the accountability day advances at the calculated Maghrib, not at midnight.

**Independent test**: with a fixed location and a controllable clock, advance across Maghrib and see
the date advance; advance across midnight and see it not.

- [ ] T029 [TEST] [US1] Create
      `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BoundaryStateStoreTest.kt` asserting:
      a fresh database resolves a date with no coordinates; storing coordinates and refreshing moves
      the regime to `Maghrib`; `resolvedDate` and `expiresAt` are populated in both cases;
      `lastResolvedDate` is persisted across a store reload.

- [X] T030 [US1] Create
      `data/src/main/kotlin/com/giraffe/mizanapp/data/time/BoundaryStateStore.kt`. It holds the
      current `BoundaryState` in an in-memory field (a `MutableStateFlow`), loads it from
      `BoundaryStateDao` on first use, and exposes `current(): BoundaryState` as a **plain
      synchronous field read**. Implement `refresh(now: Instant, zone: ZoneId)` performing the six
      steps in order, exactly as listed in
      [contracts/boundary-provider.md](./contracts/boundary-provider.md) `refresh()`.

      Three things in those steps are load-bearing and easy to get wrong:

      - **`now` and `zone` are parameters** (rule 5). The store must not read the clock, because
        `SystemTimeProvider` reads the store's resolved state and the two would otherwise form a
        cycle.
      - **Step 4, the clamp, may never be skipped and may never be widened.** Pass
        `regimeChanged = (regime != lastResolvedRegime)`, so an unchanged regime adopts the computed
        date exactly as computed (FR-023a). Clamping unconditionally credits a returning person's
        completions to a day that has already closed.
      - **Step 5 computes `expiresAt`.** Under `Maghrib` it is the *next* Maghrib — tomorrow's
        whenever `now` is already past today's, which costs a second `timesFor` call for the
        following civil date. Under `Fallback` it is the next local midnight in `zone`. It is
        non-null and must never be in the past when `refresh` returns: FR-026's rollover and
        `StreakClock`'s `dayEndsAt` are both built on it.

      `refresh()` is `suspend`; `current()` is not.

- [X] T030a [US1] Make `BoundaryStateStore` implement the `BoundaryStatus` interface from T010a.
      `current()` and `refresh(now, zone)` are the methods above; `observe()` exposes the
      `MutableStateFlow` as a read-only `Flow`. `requestLocation()` and `eraseLocation()` are filled
      in by T048 and T047 — until then they may throw `NotImplementedError`, since the only callers
      are the Phase 5 surfaces. Nothing outside `:data` may depend on the concrete store; `:app`
      injects `BoundaryStatus`, which is what T035, T051, T055 and T066 reach the boundary through.

- [X] T031 [TEST] [US1] Add to `BoundaryStateStoreTest.kt`:
      `dateAdvancesAtMaghribAndNotAtMidnight` — set coordinates and a fake Maghrib of 18:00, step the
      clock to 17:59 and assert date D, step to 18:01 and assert date D+1, step across midnight and
      assert the date does not change again.

- [X] T032 [US1] Make T031 pass. If it already passes from T030, say so and move on — do not add code
      that no test required.

      **Already passed from T030 — no production change made.**

- [X] T033 [US1] Change
      `data/src/main/kotlin/com/giraffe/mizanapp/data/time/SystemTimeProvider.kt` so `today()` returns
      `boundaryStateStore.current().resolvedDate`, replacing the whole
      `DayBoundary.dateAt(now(), zone(), null)` body T012 left there. `DayBoundary` is no longer
      imported by this file afterwards, and the store becomes its only production caller — which is
      what T067 verifies. Keep `now()` and `zone()` exactly as they are —
      this file remains the only place allowed to read the real clock. Do **not** change the
      `TimeProvider` interface: `today()` stays synchronous and non-suspending. It takes the concrete
      `BoundaryStateStore` (both live in `:data`) and may call **only** `current()` — never
      `refresh()`, which would re-enter the cycle rule 5 exists to prevent.

- [X] T034 [US1] Wire the new types into Koin in
      `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`: `LocationSource` →
      `AndroidLocationSource`, `PrayerTimesProvider` → `AdhanPrayerTimes`, and `BoundaryStateStore` as
      a **singleton** (it holds state; a factory would defeat the whole design). Bind `BoundaryStatus`
      to that **same instance** — `single<BoundaryStatus> { get<BoundaryStateStore>() }`, never a
      second construction, or the app would observe one state while `today()` reads another. Wire
      `SystemTimeProvider` to receive the store. DI wiring is exempt from test-first (Principle I).

- [X] T035 [US1] Call `boundaryStatus.refresh(timeProvider.now(), timeProvider.zone())` at app start
      in `app/src/main/java/com/giraffe/mizanapp/MizanApplication.kt` (in a coroutine, never blocking
      `onCreate`) and on resume in `MainActivity`. Inject `BoundaryStatus` and `TimeProvider`, not the
      concrete store. Follow how `ReconcileZone` is already called for the pattern.

- [X] T035a [TEST] [US1] Add to `BoundaryStateStoreTest.kt`:
      `refreshingPastExpiresAtAdvancesTheDateAndMovesExpiresAt` and
      `refreshingBeforeExpiresAtChangesNothing`. These pin the store's half of FR-026 — the resolved
      date and `expiresAt` both move at the boundary instant, and only there.

- [X] T035b [US1] Make T035a pass, then add the trigger FR-026 actually asks for: rollover while the
      app is **open**, with no start and no resume event. In `:app`, beside T035's resume hook,
      schedule a coroutine that waits until `BoundaryStatus.current().expiresAt`, calls
      `refresh(now, zone)`, and reschedules from the new `expiresAt`; cancel it when the app
      backgrounds. Drive it from the injected `TimeProvider` — it holds no clock of its own — and do
      not poll on a fixed interval: the boundary instant is known exactly, so wait for it rather than
      waking every minute.

**Checkpoint**: US1 is independently testable. Run `./gradlew :data:connectedAndroidTest`.

---

## Phase 4: User Story 2 — The week closes at Friday Maghrib (P1)

**Goal**: the week freezes at Friday's Maghrib and the next begins at the same instant.

**IMPORTANT**: research R1 established that `WeekBoundary` needs **no change at all** — Maghrib on
Friday is the start of accountability-Saturday, so "the Saturday on or before" is still correct in
accountability-date space. This phase is therefore **verification only**. If you find yourself
editing `WeekBoundary.kt`, stop — you have misunderstood the design.

- [X] T036 [TEST] [US2] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/time/MaghribWeekBoundaryTest.kt`. Using
      `DayBoundary.dateAt` with a Friday Maghrib of 18:00, assert:
      `fridayBeforeMaghribIsInTheClosingWeek`,
      `fridayAfterMaghribIsInTheNewWeek`,
      `theWeekChangesAtMaghribAndNotAtMidnight`.
      Derive the week with the **existing** `WeekBoundary.weekContaining` and assert on its `key`.

- [X] T037 [TEST] [US2] Add `everyInstantMapsToExactlyOneDayAndOneWeek` to the same file: walk a full
      year in one-hour steps with a fixed Maghrib table, collect the resolved date for each instant,
      and assert the sequence of distinct dates is contiguous with no gap and no repeat.

- [X] T038 [US2] Make T036 and T037 pass. Expected outcome: **no production change is needed**. If
      both pass with no edit, record that in the commit message. Only if one fails should you change
      anything, and then only in `DayBoundary.kt`, never in `WeekBoundary.kt`.

      **Both passed with no production change.**

- [X] T038a [TEST] [US2] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/leaderboard/LeaderboardPeriodBoundaryTest.kt`,
      covering FR-032a and FR-033 — the requirement the clarification session narrowed this whole
      feature down to, and the only no-change-expected claim with no test of its own. Take the
      accountability date from `DayBoundary.dateAt` under the Maghrib rule for a Friday-Maghrib
      instant and for the instant just before it, then assert:
      `weeklyPeriodMatchesTheWeekBoundarySpan` — `periodFor(PeriodKind.WEEKLY, date, zone, regionId)`
      returns `start` and `endInclusive` identical to `WeekBoundary.weekContaining(date)`;
      `theTwoSidesOfFridayMaghribFallInDifferentWeeklyPeriods`;
      `dailyAndMonthlyPeriodsAreUnaffected`.
      Expected outcome: **no production change** — `periodFor` already delegates to `WeekBoundary`
      and works in accountability-date space. If it passes untouched, record that in the commit
      message. Do not edit `LeaderboardPeriod.kt`, and do not touch any remote artifact (FR-032b,
      rule 8).

**Checkpoint**: `./gradlew :domain:test` passes with `WeekBoundary.kt` and `LeaderboardPeriod.kt`
untouched.

---

## Phase 5: User Story 3 — Works when it cannot tell where the person is (P1)

**Goal**: fresh install in airplane mode is fully usable; the regime is disclosed; location is
opt-in, revocation-safe, and erasable.

**This is the largest phase and the one Principle IV depends on.**

### 5a. Fallback and trust rules

- [X] T039 [TEST] [US3] Add to `BoundaryStateStoreTest.kt`:
      `freshInstallWithNoLocationUsesTheFallbackRegime`,
      `fallbackReasonIsNeverHadLocationOnAFreshInstall`,
      `currentReturnsImmediatelyWithNoCoordinates` (assert it does not suspend and does not throw).

- [X] T040 [US3] Make T039 pass in `BoundaryStateStore.kt`. The fallback path calls
      `DayBoundary.dateAt(now, zone, null)` — the same function, third argument null. Do not write a
      separate fallback code path.

      **Already passed from T030 — no production change made.**

- [X] T041 [TEST] [US3] Add `ninetyOfflineDaysWithAnUnchangedZoneKeepTheMaghribRegime`: store
      coordinates, advance the clock 90 days without changing the zone and without a new fix, and
      assert the regime is still `Maghrib` at every step. Age must never invalidate coordinates
      (FR-012a).

- [X] T042 [TEST] [US3] Add `aZoneIdChangeWithNoFreshFixMovesToTheFallback` and
      `aDaylightSavingOffsetChangeDoesNotInvalidateCoordinates`. The second must compare zone
      **identifiers**, not offsets — `Africa/Cairo` stays `Africa/Cairo` across DST.

- [X] T043 [US3] Implement the trust rules in `BoundaryStateStore.refresh()`: compare the device zone
      id against `zoneIdWhenObtained`; on a mismatch with no fresh fix, set
      `Fallback(ZONE_CHANGED_AWAITING_FIX)`. Never compare ages, never compare offsets.

      **Already implemented in T030 — no production change made.**

- [X] T044 [TEST] [US3] Add `aFreshFixAfterAZoneChangeResumesTheMaghribRegime` (FR-012c), then make
      it pass.

      **Already passed from T030 — no production change made.**

### 5b. Permission revocation and erasure

- [X] T045 [TEST] [US3] Add `revokedPermissionKeepsTheMaghribRegimeFromRetainedCoordinates`
      (FR-017a) — set `hasPermission()` false on the fake while coordinates are stored, and assert the
      regime does not change.

- [X] T046 [TEST] [US3] Add `erasingCoordinatesMovesToTheFallbackWithReasonErased` and
      `erasingCoordinatesLeavesDayPlansAndCompletionsUnchanged` — the second inserts a day plan and
      completions, erases, and asserts every stored row is identical afterwards (FR-017d).

- [X] T047 [US3] Implement `eraseLocation()` in `BoundaryStateStore`: null `latitude`, `longitude`,
      `zoneIdWhenObtained` and `obtainedAt` in place; set the regime to `Fallback(ERASED)`; re-resolve
      through the clamp. It must not delete the row and must not touch any other table.

      **Already implemented in T030 — no production change made.**

- [X] T048 [US3] Implement `requestLocation()` returning the `LocationRequestOutcome` from
      [contracts/boundary-provider.md](./contracts/boundary-provider.md). It may only be reached from
      an explicit user action.

      **Already implemented in T030 — no production change made.**

### 5c. First-launch prompt

- [X] T049 [TEST] [US3] Add to `app/src/test/java/com/giraffe/mizanapp/today/` a
      `LocationPromptStateTest.kt` asserting: the prompt is visible on first launch and invisible once
      `promptShown` is true; `DismissLocationPrompt` hides it and changes nothing else; the Today
      state is fully populated while the prompt is visible (it is a field, never a gate).

- [X] T050 [US3] Add the `LocationPrompt` field to
      `app/src/main/java/com/giraffe/mizanapp/today/TodayUiState.kt` and the two events to its event
      sealed interface, exactly as in [contracts/ui-state.md](./contracts/ui-state.md) §1.

- [X] T051 [US3] Handle both events in
      `app/src/main/java/com/giraffe/mizanapp/today/TodayViewModel.kt`. `EnableLocation` calls
      `requestLocation()`; `DismissLocationPrompt` sets `promptShown` and hides it. The permission
      dialog must be raised **only** from `EnableLocation` — never on launch, never as a side effect.

- [X] T052 [US3] Render the prompt in
      `app/src/main/java/com/giraffe/mizanapp/today/TodayScreen.kt` as a dismissible card above the
      existing content. Copy: say that location enables accurate local prayer times and the
      Maghrib-based Islamic day boundary; buttons "Enable location" and "Not now". **No warning
      colours, no consequence framing, no repeat nagging after dismissal** (FR-007e, Principle IX).
      Use the design tokens in `CLAUDE.md`.

### 5d. Settings section

- [X] T053 [TEST] [US3] Create
      `app/src/test/java/com/giraffe/mizanapp/profile/LocationSettingsStateTest.kt` asserting a
      distinct, non-empty `statement` for each of the four regimes in
      [contracts/ui-state.md](./contracts/ui-state.md) §2, and that
      `Fallback(ZONE_CHANGED_AWAITING_FIX)` produces one — that case must never be silent (FR-012d).

- [X] T054 [US3] Add `LocationSettings` to
      `app/src/main/java/com/giraffe/mizanapp/profile/ProfileUiState.kt` and the four events to
      `ProfileEvent`, per the contract.

- [X] T055 [US3] Handle the events in
      `app/src/main/java/com/giraffe/mizanapp/profile/ProfileViewModel.kt`, observing
      `BoundaryStatus.observe()`. Erasing goes through a confirmation, following the existing sign-out
      confirmation pattern already in this file.

- [X] T056 [US3] Render the section in
      `app/src/main/java/com/giraffe/mizanapp/profile/ProfileScreen.kt`: the statement, whether a
      location is held, an "Enable location" action when it is not, and an erase action with
      confirmation when it is. The confirmation must say the boundary returns to local midnight and
      must **not** suggest any recorded history changes.

- [X] T057 [TEST] [US3] Create
      `app/src/androidTest/.../FreshInstallNoLocationTest.kt` covering quickstart Scenario 3: the app
      renders and records with no location and **no system permission dialog raised** (SC-017).

**Checkpoint**: US3 independently testable. Run all four test commands.

---

## Phase 6: User Story 4 — Existing history reads exactly as it did (P1)

**Goal**: nothing already closed changes. Principle III, non-negotiable.

- [X] T058 [TEST] [US4] Create
      `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/BoundaryChangeHistoryImmutabilityTest.kt`
      — **this is the FR-025 test the constitution requires and it gates completion.** Seed several
      day plans and completions and one closed week under the fallback regime; capture every day's
      earned points, available points, percentage and Hijri label plus the weekly total; switch to the
      Maghrib regime with coordinates; re-read everything and assert byte-identical figures.

- [X] T059 [TEST] [US4] Add `completionCreditedDateIsNeverRewritten` to the same file: assert every
      `completions.creditedDate` value is unchanged after the regime switch (FR-031).

- [X] T060 [US4] Make T058 and T059 pass. Expected outcome: **no production change is needed** — the
      clamp changes only future resolution and `DayPlanRepository` has no update method. If either
      test fails, you have written a code path that rewrites history; delete it rather than adjusting
      the test.

      **Both passed with no production change.**

- [X] T058a [TEST] [US4] Add `hijriLabelsAreStillComputedLocallyForMaghribBoundaryDays` to the same
      file, covering FR-034 and FR-035: a day plan created under the `Maghrib` regime carries exactly
      the label `HijriLabel.forDate` returns for its accountability date — computed on-device, with
      no network call and no synced calendar lookup — so the label follows the accountability date
      rather than defining it. Together with T058's assertion that stored labels never change
      (FR-036), this is the whole of the Hijri requirement group.

- [X] T061 [TEST] [US4] Add `changingTheRegionConventionMappingLeavesClosedDaysUnchanged` (FR-003e,
      SC-016).

**Checkpoint**: `./gradlew :data:connectedAndroidTest` passes. This phase gates the increment.

---

## Phase 7: User Story 5 — Every screen agrees about what day it is (P2)

**Goal**: no leftover second opinion in the window between Maghrib and midnight.

- [X] T062 [TEST] [US5] Create
      `app/src/test/java/com/giraffe/mizanapp/BoundaryAgreementTest.kt`: with the clock between
      Maghrib and midnight, build the Today, Week, Streak, History and Insights states from the same
      fakes and assert all report the same accountability date and the same week key.

- [X] T063 [US5] Make T062 pass. Expected outcome: **no production change is needed** — every consumer
      already reads `TimeProvider.today()` (research R2). If one fails, find the code converting an
      instant to a date itself and route it through `TimeProvider` instead. Do **not** add a second
      conversion.

- [X] T064 [TEST] [US5] Create
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/StreakClockTest.kt` (replace it if it
      exists) asserting: `atRiskPointIsAlwaysInsideItsOwnDay` across a full year at a high and a low
      latitude, and `nextBoundaryIsTheEarlierOfAtRiskAndDayEnd`.

- [X] T065 [US5] Change `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/StreakClock.kt`:
      replace `AT_RISK_FROM: LocalTime = 20:00` with `AT_RISK_BEFORE_END: Duration` (use 4 hours);
      change `isAtRiskWindow(now, zone)` to `isAtRiskWindow(now, dayEndsAt)` returning
      `!now.isBefore(dayEndsAt.minus(AT_RISK_BEFORE_END))`; change
      `nextBoundaryAfter(now, zone)` to `nextBoundaryAfter(now, dayEndsAt)` returning the earlier of
      the at-risk instant and `dayEndsAt` that is strictly after `now`.

- [X] T066 [US5] Update the callers of the changed `StreakClock` methods:
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/streak/BuildStreakSummary.kt` and
      `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetStreakSummary.kt`. They now need
      the day's end, which comes from `BoundaryState.expiresAt` — pass it in as a parameter rather
      than reading it inside the pure function. Update
      `domain/src/test/kotlin/com/giraffe/mizanapp/domain/streak/` tests that break.

**Checkpoint**: `./gradlew test` passes.

---

## Phase 8: Polish & Cross-Cutting

- [X] T067 [P] Verify `DayBoundary.dateAt` still has **exactly one** production caller:
      `grep -rn "DayBoundary" --include=*.kt domain/src/main data/src/main app/src/main`. After T033
      the only hit outside `DayBoundary.kt` itself must be `BoundaryStateStore.kt` — **not**
      `SystemTimeProvider.kt`, which held it until T012 and hands it to the store at T033. Two hits
      means the transitional call was left behind; more than two means a caller is converting instants
      to dates on its own, and it must go through `TimeProvider` instead (SC-013, FR-010).

      **Verified** — the only non-comment hit outside `DayBoundary.kt` is `BoundaryStateStore.kt`.

- [X] T068 [P] Verify `:domain` purity: `domain/build.gradle.kts` must have gained no dependency, and
      `grep -rn "adhan\|kotlinx.datetime\|android\." domain/src/main` must return nothing.

      **Verified clean** — no dependency added, grep returns nothing.

- [X] T068a [P] Verify the other half of SC-013 and FR-001 — one prayer-time calculator, one location
      reader. `grep -rn "adhan" --include=*.kt data/src/main app/src/main` must hit
      `AdhanPrayerTimes.kt` and nothing else.
      `grep -rn "LocationManager\|android\.location\|checkSelfPermission" --include=*.kt data/src/main app/src/main`
      must hit `AndroidLocationSource.kt` and nothing else. `ACCESS_FINE_LOCATION` must appear
      nowhere at all, the manifest included (FR-005).

      **Verified** — both greps hit only their one file each; `ACCESS_FINE_LOCATION` appears nowhere.

- [X] T069 [P] Verify no coordinate ever leaves the device: grep the whole tree for logging or
      serialization of `latitude`/`longitude` and confirm neither appears in any DTO under
      `data/src/main/kotlin/com/giraffe/mizanapp/data/sync/dto/` (SC-011, FR-006).

      **Verified** — no hits in `data/sync/`, no logging calls found in the prayer/boundary files.

- [X] T070 [P] Review every string added in T052 and T056 against the no-shame standard in
      `CLAUDE.md` "Design" and Principle IX. Zero exceptions permitted (SC-018).

      **Reviewed** — no red, no warning icon, no failure/consequence framing in any string added.

- [X] T071 [P] Confirm `data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/5.json` is committed
      and that `1.json` through `4.json` are unmodified (`git diff` must show no change to them).

      **Verified** — 5.json committed (eb82c00); `git diff origin/develop-v1` shows no change to 1-4.json.

- [ ] T072 Run every quickstart scenario in [quickstart.md](./quickstart.md) manually on a device.
      Scenario 7 is gating.

- [ ] T073 Run the full suite: `./gradlew build test connectedAndroidTest`. All green.

- [ ] T074 Open the pull request into `develop-v1`. In the body, state that the Constitution Check in
      `plan.md` passes and name the principles touched. Confirm before requesting merge that every
      `[TEST]` commit precedes its implementation commit in the PR's commit history — this is checked
      at the merge gate and cannot be verified after a squash merge.

---

## Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational) ─── BLOCKS EVERYTHING BELOW
    ↓
Phase 3 (US1) ──→ Phase 4 (US2) ──→ Phase 7 (US5)
    ↓                                    ↑
Phase 5 (US3) ───────────────────────────┤
    ↓                                    │
Phase 6 (US4) ───────────────────────────┘
    ↓
Phase 8 (Polish)
```

- **Phase 2 is a hard gate.** Nothing in Phase 3+ compiles without it.
- US2, US4 and US5 are largely **verification** phases — research R1, R7 and R2 predict no production
  change is needed in them. That prediction is itself the thing being tested.
- US3 is the largest phase and the only one with substantial UI.

## Parallel opportunities

- **T005–T010a** — seven independent new domain files, no shared state. Safe to do together.
- **T003 and T004** — manifest and seed resource, unrelated files.
- **T067–T071** — six independent verification greps and reviews (T068a included).
- Within Phase 5, **5a (T039–T048)** and **5c/5d (T049–T056)** touch different modules and can
  proceed in parallel once T030 exists.

Everything else is sequential because of the test-then-implement rule.

## Implementation strategy

**MVP scope**: Phase 1 + Phase 2 + Phase 3 (US1). At that point the day genuinely turns over at
Maghrib for anyone with a location, and the app still works for anyone without one — because the
fallback is the code path Phase 2 built, not something Phase 5 adds.

**Ship order**: US1 → US3 → US2 → US4 → US5. US3 comes second despite being third in the spec because
it is what makes the feature safe for a person who never grants location.

**Do not skip Phase 6.** It is short, it is mostly assertions, and it is the one phase whose failure
means the increment cannot merge under any circumstances.

## Task summary

| Phase | Tasks | Count |
|---|---|---|
| 1 — Setup | T001–T004 | 4 |
| 2 — Foundational | T005–T028, plus T010a and T024a | 26 |
| 3 — US1 | T029–T035, plus T030a, T035a, T035b | 10 |
| 4 — US2 | T036–T038, plus T038a | 4 |
| 5 — US3 | T039–T057 | 19 |
| 6 — US4 | T058–T061, plus T058a | 5 |
| 7 — US5 | T062–T066 | 5 |
| 8 — Polish | T067–T074, plus T068a | 9 |
| **Total** | | **82** |

Eight tasks were added and four reopened after `/speckit-analyze` on 2026-08-30: `BoundaryStatus` had
no task at all (T010a, T030a), the clamp was armed on every resolution rather than at the seam (T014,
T015, and FR-023a in the spec), `MIGRATION_3_4` was never registered (T024a), the test doubles were
placed where the tests that use them cannot see them (T028), `expiresAt` was never computed (T030),
FR-026's in-app rollover had no trigger (T035a, T035b), FR-032a/FR-033 and FR-034/FR-035 had no
verification (T038a, T058a), and the second half of SC-013 was unchecked (T068a).
