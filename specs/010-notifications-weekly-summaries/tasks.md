---

description: "Task list for spec 010 — Notifications and Weekly Summaries"
---

# Tasks: Notifications and Weekly Summaries

**Input**: Design documents from `/specs/010-notifications-weekly-summaries/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: MANDATORY, not optional. Constitution Principle I is non-negotiable on this project: no
production code may be written before a failing test that requires it. **Every production file below
has a test task numbered before it**, with only the three exceptions the constitution itself names —
DI wiring, `@Preview` composables, and generated code. Mappers, platform adapters, broadcast
receivers, workers and Compose surfaces are all explicitly *not* exempt. Do not batch a test and its
implementation into one commit; the pull request's commit history is what the merge gate reads.

**Organization**: Grouped by user story so each can be implemented, tested and demonstrated on its own.

**Revision**: This list was rewritten after `/speckit-analyze`, which found two CRITICAL and three HIGH
issues in the first version. What changed: five missing test tasks added (T025, T033, T035, T037, T039);
`buildNotificationPlan` now returns `NotificationPlan(anchors, refreshAt)` because the day-end refresh
wake cannot be typed as a notification anchor; `weekClosesAt` gained a named derivation (T014–T015)
instead of being an undefined parameter; three Compose surfaces gained tests (T056, T061, T096); and
the whole list was renumbered.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Safe to do in parallel — different file, no dependency on an unfinished task
- **[Story]**: `[US1]`–`[US4]`, matching the user stories in [spec.md](./spec.md)
- Every task names an exact file path

---

## How to work these tasks

Read this section before starting. It exists so each task below can be short.

### The one-line summary of the design

Two pure Kotlin functions in `:domain` decide everything. Android only supplies instants and delivers
broadcasts. If you find yourself writing an `if` about notifications inside `:data` or `:app`, the
decision belongs in `:domain` instead.

### Vocabulary — one word per idea

Use these exact words in code, test names and comments. See [spec.md](./spec.md) §Terminology.

| Word | Means |
|---|---|
| **post** | Show a notification to the person. Never "announce", "deliver", "send" |
| **schedule** | Register an anchor so the device wakes at its instant. Scheduling is not posting |
| **fire time** | The moment a scheduled anchor is evaluated. It does not mean anything was shown |
| **withdraw** | Remove an already-posted notification. Never "cancel", "dismiss" |
| **derive** | Compute a weekly summary's figures on demand. Summaries are never stored |

### Module rules — these fail the build or the review if broken

| Rule | Meaning |
|---|---|
| `:domain` has **zero** Android imports | No `android.*`, no `androidx.*`, no `Context`. Only Kotlin, stdlib, coroutines, `java.time` |
| `:app` → `:data` → `:domain` | Never the reverse. `:domain` imports nothing from the other two |
| Koin only | Never add Hilt, never add `@Inject` |
| One time source | Never call `Instant.now()`, `LocalDate.now()`, `System.currentTimeMillis()`, or `ZoneId.systemDefault()` in any file you create. Take `now` and `zone` as parameters, or inject `TimeProvider` |
| One location/prayer source | Never construct a prayer time. Read `PrayerTimesProvider` / `BoundaryStatus` |
| One week rule | Never compute a week start or a week close except through `WeekBoundary` and `WeekCloseInstant` |
| Never write history | Never call `DayPlanRepository.ensurePlanFor(...)` or `CompletionRepository.record/undoLast(...)` from any file you create in this feature |

### Existing code you will call (already built, do not modify)

```kotlin
// domain/time/TimeProvider.kt
interface TimeProvider { fun now(): Instant; fun today(): LocalDate; fun zone(): ZoneId }

// domain/time/BoundaryStatus.kt   — current().expiresAt is the instant this accountability day ends
interface BoundaryStatus {
    fun current(): BoundaryState
    fun observe(): Flow<BoundaryState>
    suspend fun refresh(now: Instant, zone: ZoneId)
    /* ... */
}

// domain/time/BoundaryState.kt
data class BoundaryState(val regime: BoundaryRegime, val coordinates: Coordinates?, /* ... */
                         val resolvedDate: LocalDate, val expiresAt: Instant, /* ... */)
// BoundaryRegime is either BoundaryRegime.Maghrib or BoundaryRegime.Fallback(reason)

// domain/prayer/PrayerTimesProvider.kt
interface PrayerTimesProvider { suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome }
// PrayerTimesOutcome = Calculated(times) | NoLocation | CalculationFailed(reason)
// PrayerTimes(date, fajr, dhuhr, asr, maghrib, isha) — all Instant

// domain/time/WeekBoundary.kt
object WeekBoundary { fun startOfWeek(date: LocalDate): LocalDate; fun weekContaining(date: LocalDate): Week }

// domain/week/Week.kt
@JvmInline value class WeekKey(val value: String)          // the week's Saturday, as an ISO string
data class Week(val key: WeekKey, val start: LocalDate, val dates: List<LocalDate>)  // week.end == dates.last()

// domain/streak/StreakClock.kt
object StreakClock {
    val AT_RISK_BEFORE_END: Duration              // 4 hours — reuse, never restate
    fun isAtRiskWindow(now: Instant, dayEndsAt: Instant): Boolean
    fun nextBoundaryAfter(now: Instant, dayEndsAt: Instant): Instant
}

// domain/streak/StreakSummary.kt — fields you need: current, todayCounted
// domain/day/DayPlan.kt          — sectionsInOrder(): List<Pair<String, List<PlannedTask>>>
// domain/day/Occurrences.kt      — liveCount(completions, taskSlug): Int
// domain/week/BuildWeekSummary.kt — buildWeekSummary(week, today, recordStart, plans, completions, projectedAvailable, coverage)
// domain/week/ProjectAvailablePoints.kt — projectAvailablePoints(catalogue, version, date): Int
// domain/repository/CatalogueRepository.kt — versionEffectiveOn(date): Int?, catalogueAt(version): Catalogue?
```

### Section ids in the catalogue

`fajr`, `dhuhr`, `asr`, `maghrib`, `isha`, `qiyam-witr`, `quran`, `adhkar`, `fasting`, `friday`.
**Only the first five ever produce a nudge.** The other five map to nothing (FR-014).

### Test conventions in this repo

- `:domain` unit tests → `domain/src/test/kotlin/com/giraffe/mizanapp/domain/<package>/`
- `:app` unit tests → `app/src/test/java/com/giraffe/mizanapp/<package>/`
- `:data` instrumentation → `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/`
- `:app` instrumentation → `app/src/androidTest/kotlin/com/giraffe/mizanapp/`
- JUnit4 (`org.junit.Test`, `org.junit.Assert.*`), `kotlinx.coroutines.test.runTest` for suspend code

### Every commit

Run the layer you touched: `./gradlew :domain:test`, `./gradlew :app:test`, or
`./gradlew :data:connectedAndroidTest`. Never commit a red test.

---

## Phase 1: Setup

**Purpose**: Permissions and the empty package structure. No logic.

- [X] T001 Add three permissions to `app/src/main/AndroidManifest.xml` above the existing `ACCESS_COARSE_LOCATION` line: `android.permission.POST_NOTIFICATIONS`, `android.permission.SCHEDULE_EXACT_ALARM`, `android.permission.RECEIVE_BOOT_COMPLETED`. Do NOT add `USE_EXACT_ALARM` — Play policy restricts it to alarm-clock and calendar apps (research R1).
- [X] T002 [P] Create empty directory `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/`
- [X] T003 [P] Create empty directory `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/`
- [X] T004 [P] Create empty directories `app/src/main/java/com/giraffe/mizanapp/notifications/` and `app/src/main/java/com/giraffe/mizanapp/weeklysummary/`

**Checkpoint**: `./gradlew :app:assembleDebug` still succeeds. No new Gradle dependency is needed anywhere in this feature — do not edit any `build.gradle.kts` or `libs.versions.toml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The value types, storage and platform ports every user story needs.

**⚠️ CRITICAL**: No user story phase may begin until this whole phase is done and green.

### Domain value types — test, then implementation, every time

- [X] T005 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/QuietHoursTest.kt`. Cases: (a) window 22:00→06:00 contains 23:30 and 02:00, excludes 12:00; (b) window 13:00→14:00 contains 13:30 only; (c) `endAfter` for an instant inside a midnight-crossing window returns the *next* 06:00, not today's; (d) `endAfter` for an instant inside a same-day window returns today's end; (e) a window where start == end contains every instant and `endAfter` returns start plus 24 hours. Build instants with `LocalDateTime.of(...).atZone(ZoneId.of("Africa/Cairo")).toInstant()`. Run it; it must fail to compile — that is the expected first failure.
- [X] T006 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/QuietHours.kt`: `data class QuietHours(val start: LocalTime, val end: LocalTime)` with `fun contains(instant: Instant, zone: ZoneId): Boolean` and `fun endAfter(instant: Instant, zone: ZoneId): Instant`. Make T005 pass.
- [X] T007 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationCategory.kt`: `enum class NotificationCategory { PRAYER_WINDOW, STREAK_AT_RISK, WEEKLY_SUMMARY }`. Exactly three values, now and forever (FR-001). No test — a bare enum has no behaviour.
- [X] T008 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationPreferences.kt` per [data-model.md](./data-model.md) §NotificationPreferences: `data class NotificationPreferences(val enabled: Set<NotificationCategory>, val allSilenced: Boolean, val quietHours: QuietHours?)` plus `companion object { val DEFAULT = NotificationPreferences(setOf(WEEKLY_SUMMARY), false, null) }`. The default is FR-003: summary on, the other two off.
- [X] T009 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/NotificationAnchorTest.kt`. Cases: `anchorKey` for `PrayerWindow(2026-09-04, "asr", …)` equals `"PRAYER:2026-09-04:asr"`; for `Day(2026-09-04)` equals `"STREAK:2026-09-04"`; for `ClosedWeek(WeekKey("2026-08-29"))` equals `"WEEK:2026-08-29"`; two anchors with the same subject produce the same key.
- [X] T010 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationAnchor.kt` with `AnchorSubject` (sealed: `PrayerWindow(date, sectionId, windowEndsAt)`, `Day(date)`, `ClosedWeek(key)`), `data class NotificationAnchor(category, firesAt: Instant, speaksFor: AnchorSubject)` and `val NotificationAnchor.anchorKey: String`. Make T009 pass.
- [X] T011 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationVerdict.kt` with `NotificationContent`, `NotificationVerdict` (`Post`, `Discard`, `Hold`) and the `DiscardReason` enum, copied verbatim from [data-model.md](./data-model.md). All eleven reasons must be present even though nothing produces them yet.
- [X] T012 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/DeliveryLedgerTest.kt`. Cases: `terminalFor` returns the row for `DELIVERED`; returns the row for `DISCARDED`; returns **null** for `HELD` (a held summary must still be postable); returns null for an unknown key.
- [X] T013 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/DeliveryLedger.kt`: `data class DeliveryRecord(val anchorKey: String, val category: NotificationCategory, val state: DeliveryState, val reason: DiscardReason?, val decidedAt: Instant, val heldUntil: Instant?)`, `enum class DeliveryState { DELIVERED, DISCARDED, HELD }`, and `fun List<DeliveryRecord>.terminalFor(anchorKey: String): DeliveryRecord?`. Make T012 pass.
- [X] T014 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/WeekCloseInstantTest.kt`. Cases: (a) a boundary whose `resolvedDate` is the week's Friday returns exactly `boundary.expiresAt`; (b) a boundary on the Saturday, Sunday … Thursday of that week returns **null**; (c) the function never calls a clock and never constructs an instant of its own — assert by passing a boundary whose `expiresAt` is an arbitrary literal and checking it comes back unchanged.
- [X] T015 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/WeekCloseInstant.kt`: `fun weekCloseInstant(boundary: BoundaryState): Instant?` implementing exactly the three-step rule in [contracts/notification-plan.md](./contracts/notification-plan.md) §"Who computes weekClosesAt". **This is the only place a week-close instant is derived** (Principle VII). It must not project a future Maghrib. Make T014 pass.

### Ports

- [X] T016 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationScheduler.kt` — the interface from [contracts/notification-plan.md](./contracts/notification-plan.md) §Ports, plus `enum class DeliveryMode { EXACT, RELAXED }`. Interface only; no implementation, so no test.
- [X] T017 [P] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NotificationPresenter.kt` — the interface from the same contract section. Interface only; no implementation, so no test.

### Room storage

- [X] T018 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/NotificationMigrationTest.kt` — **the Principle III gate for this increment**. Copy the structure of the existing `BoundaryStateMigrationTest.kt`. Seed a `day_plans` row and a `completions` row at schema version 5, run `MIGRATION_5_6`, then assert every column of both rows is unchanged, and that tables `notification_preferences` and `notification_deliveries` now exist. This must exist and fail before T019.
- [X] T019 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/NotificationPreferencesEntity.kt` with exactly the columns in [data-model.md](./data-model.md) §notification_preferences, `@PrimaryKey val id: Int = 0`, and `@ColumnInfo(defaultValue = ...)` on every non-null column. No `userId`, no `updatedAt`, no tombstone — this table is deliberately not synchronisable.
- [X] T020 [P] Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/entities/NotificationDeliveryEntity.kt` with exactly the columns in §notification_deliveries and `@PrimaryKey val anchorKey: String`.
- [X] T021 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/db/daos/NotificationDao.kt` with: `suspend fun preferences(): NotificationPreferencesEntity?`, `fun observePreferences(): Flow<NotificationPreferencesEntity?>`, `@Insert(onConflict = REPLACE) suspend fun upsertPreferences(e)`, `suspend fun deliveries(): List<NotificationDeliveryEntity>`, `suspend fun delivery(anchorKey: String): NotificationDeliveryEntity?`, `@Insert(onConflict = REPLACE) suspend fun upsertDelivery(e)`, `@Query("DELETE FROM notification_deliveries WHERE decidedAt < :before") suspend fun pruneBefore(before: Long)`.
- [X] T022 In `data/src/main/kotlin/com/giraffe/mizanapp/data/db/MizanDatabase.kt`: add both entities to the `@Database` entity list, bump `version = 5` to `version = 6`, add `abstract fun notificationDao(): NotificationDao`, and add a `MIGRATION_5_6` that runs only two `CREATE TABLE IF NOT EXISTS` statements. Register it wherever `MIGRATION_4_5` is registered. Never add `fallbackToDestructiveMigration`. Make T018 pass, then commit the exported schema JSON Room writes to `data/schemas/`.
- [X] T023 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/NotificationPreferencesStoreTest.kt`. Cases: a fresh database returns `NotificationPreferences.DEFAULT` (summary on, others off, not silenced, no quiet hours); saving and reloading round-trips every field including a midnight-crossing quiet-hours window; `observePreferences` emits after a save.
- [X] T024 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/NotificationPreferencesStore.kt` mapping the entity to and from the domain `NotificationPreferences`, returning `DEFAULT` when the row is absent. Make T023 pass.
- [X] T025 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DeliveryStoreTest.kt`. Cases: writing then reading a record round-trips every field including a null `reason` and a null `heldUntil`; writing the same `anchorKey` twice leaves exactly one row (the primary key is the idempotency guarantee); `pruneBefore` removes only rows older than the cutoff; an empty table reads as an empty list, never null. **A mapper is explicitly not exempt from Principle I** — this test must exist before T026.
- [X] T026 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/DeliveryStore.kt` — reads the ledger as `List<DeliveryRecord>`, writes one record, prunes rows older than 90 days. Pure mapping; no decisions. Make T025 pass.

### The shared decision skeleton

At this stage the two functions handle only the rules that apply to every category. Each user story
phase adds its own branch.

- [X] T027 Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/BuildNotificationPlanTest.kt` with only the shared cases: (a) `allSilenced = true` returns `anchors` empty **but `refreshAt` still populated**; (b) a category absent from `preferences.enabled` contributes no anchor; (c) an anchor whose key is terminal in the ledger is absent; (d) an anchor whose `firesAt` is at or before `now` is absent; (e) `refreshAt` always equals `boundary.expiresAt`, under every combination of preferences. Add a `fixtures()` helper in the test file so later phases can extend it.
- [X] T028 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/BuildNotificationPlan.kt` with the exact signature in [contracts/notification-plan.md](./contracts/notification-plan.md), **returning `NotificationPlan(anchors, refreshAt)`** — not a bare list. Define `NotificationPlan` in the same file. Implement only the shared rules; return no anchors for any category for now. Make T027 pass. Read the contract's "Why `refreshAt` is a separate field" note before starting: the day-end wake is not a fourth category and must not become one.
- [X] T029 Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/EvaluateAnchorTest.kt` with the shared cases, asserting the exact `DiscardReason` each time and, critically, the **order** of the checks: terminal ledger → `ALREADY_DELIVERED`; no permission → `NO_PERMISSION`; silenced → `ALL_SILENCED`; category off → `CATEGORY_OFF`; subject names a different date than the boundary's `resolvedDate` → `DAY_ROLLED_OVER`. Include one case proving `ALREADY_DELIVERED` wins over `NO_PERMISSION` when both apply.
- [X] T030 Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/EvaluateAnchor.kt` with the exact signature in the contract. Implement steps 1–5 of the numbered order there; for every other case return `Post` with placeholder content for now. Make T029 pass.

### Platform mechanism — every adapter gets a test first

- [X] T031 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/AlarmNotificationSchedulerTest.kt`. Cases: `replaceAll` with three anchors then `replaceAll` with one leaves exactly one pending `PendingIntent` (check with `PendingIntent.getBroadcast(..., FLAG_NO_CREATE)`); `cancelAll` leaves none; `deliveryMode()` returns `RELAXED` when exact alarms are unavailable and `EXACT` when they are.
- [X] T032 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/AlarmNotificationScheduler.kt` implementing `NotificationScheduler` over `AlarmManager`. One `PendingIntent` per `anchorKey` (use the key's `hashCode()` as the request code and put the key in the intent extras). Use `setExactAndAllowWhileIdle` when `alarmManager.canScheduleExactAlarms()`, otherwise `setAndAllowWhileIdle`. On API < 31 `canScheduleExactAlarms` does not exist — treat it as true. Make T031 pass.
- [X] T033 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/AndroidNotificationPresenterTest.kt`. Cases: posting creates one active notification with the anchor key as its tag; `withdraw` removes it; `withdraw` for a key never posted is a no-op and does not throw; posting twice for the same key replaces rather than stacks; each category gets its own channel and the channels are created before the first post.
- [X] T034 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/AndroidNotificationPresenter.kt` implementing `NotificationPresenter` over `NotificationManagerCompat`, one channel per category. `hasPermission()` must read the live platform state on every call — never cache it. Make T033 pass.
- [X] T035 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/NotificationTriggerReceiverTest.kt`. Cases: delivering an intent carrying an `anchorKey` enqueues exactly one `NotificationWorker` with that key as its unique work name; the receiver performs no database access (assert the database is untouched); delivering the same key twice collapses to one pending request.
- [X] T036 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/NotificationTriggerReceiver.kt`: a `BroadcastReceiver` that reads the `anchorKey` extra, enqueues `NotificationWorker` with that key as unique work, and returns. **No database access, no logic, no clock read.** Make T035 pass.
- [X] T037 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/SystemEventReceiverTest.kt`. Cases: each of `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED` and `TIME_SET` enqueues a bare refresh with no anchor key; an unrelated action enqueues nothing.
- [X] T038 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/SystemEventReceiver.kt` for those four actions, enqueuing a bare refresh. Register both receivers in `app/src/main/AndroidManifest.xml`: `SystemEventReceiver` with `android:exported="true"` and the matching intent filters, `NotificationTriggerReceiver` with `android:exported="false"`. Make T037 pass.
- [X] T039 Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/NotificationWorkerTest.kt` — **the most important test in Phase 2**. Using fakes for `NotificationScheduler` and `NotificationPresenter`, assert the worker: (a) refreshes `BoundaryStatus` before reading anything; (b) calls `evaluateAnchor` for the triggering anchor and acts on its verdict rather than deciding for itself — feed it a fake verdict of `Discard(SECTION_COMPLETE)` and assert nothing is posted; (c) writes exactly one ledger row per run; (d) calls `buildNotificationPlan` and hands `anchors` to `replaceAll` on **every** run, including a bare refresh with no triggering anchor; (e) schedules `refreshAt` even when `anchors` is empty; (f) prunes ledger rows older than 90 days.
- [X] T040 Create `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/NotificationWorker.kt` (a `CoroutineWorker`) performing exactly the six steps in [contracts/platform-ports.md](./contracts/platform-ports.md) §NotificationWorker, deriving `weekClosesAt` through `weekCloseInstant(boundary)` and nowhere else. Constructor takes `TimeProvider`, `BoundaryStatus`, `PrayerTimesProvider`, the repositories, the stores, the scheduler and the presenter. Make T039 pass.
- [X] T041 Wire everything into Koin in `app/src/main/java/com/giraffe/mizanapp/di/Modules.kt`, inside the existing `dataModule`: `single { notificationDaoOf(get()) }`, `single { NotificationPreferencesStore(get()) }`, `single { DeliveryStore(get()) }`, `single<NotificationScheduler> { AlarmNotificationScheduler(androidContext()) }`, `single<NotificationPresenter> { AndroidNotificationPresenter(androidContext()) }`, and `worker { NotificationWorker(...) }` following the existing `SyncWorker` line exactly. DI wiring is the one thing Principle I exempts.

**Checkpoint**: `./gradlew :domain:test :app:test` green, `./gradlew :data:connectedAndroidTest` green, app builds and runs unchanged. Nothing is posted yet because no category branch exists — but the device now wakes at each day's end.

---

## Phase 3: User Story 1 — The week closes and the person is told how it went (Priority: P1) 🎯 MVP

**Goal**: At week close a summary is derived and posted, and a Weekly Summary screen shows closed weeks on demand.

**Independent Test**: With a fixed location and a controllable clock, advance across Friday's Maghrib and confirm a summary is posted for the closed week with figures matching the weekly sheet, and that the screen shows the same figures whether or not a notification was ever posted.

### Read-only summary

- [X] T042 [P] [US1] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/week/SummaryDormancyTest.kt`. Cases: zero empty weeks → active; one empty week → active; two consecutive empty weeks → active (both are still posted); three consecutive → dormant; two empty then one with activity then two empty → active (the run resets); a week with activity after dormancy → active again.
- [X] T043 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/week/SummaryDormancy.kt`: `fun isSummaryDormant(closedWeeksNewestFirst: List<Boolean>): Boolean` where each `Boolean` is "this closed week had at least one recorded completion". Return true when the three most recent closed weeks are all empty. Make T042 pass.
- [X] T044 [P] [US1] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/GetClosedWeekSummaryTest.kt`. Cases: (a) a week with stored plans for every day returns the same `WeekSummary` figures as `buildWeekSummary` given the same inputs; (b) a week with two unopened elapsed days projects their available points using `versionEffectiveOn(date)`, not the current version; (c) **the immutability case** — build a summary, change the catalogue's points and schedules to a new version, rebuild, assert every figure is identical; (d) a week with no completions returns zero earned and a non-zero available; (e) **a week only partly inside recorded history returns a populated `CoverageNote`** describing the covered span (FR-029).
- [X] T045 [US1] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/usecase/GetClosedWeekSummary.kt`. It takes `DayPlanRepository`, `CompletionRepository`, `CatalogueRepository` and `RecordCoverageRepository` — **not** `TimeProvider` — and it must never call `ensurePlanFor`. Read stored plans with `plansBetween`, live completions with `liveBetween`, project each elapsed date with no stored plan using `projectAvailablePoints(catalogueAt(versionEffectiveOn(date)), version, date)`, then call the existing `buildWeekSummary`. Make T044 pass. See research R4 for why reusing `GetWeekSummary` is forbidden.
- [X] T046 [US1] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/usecase/SummaryAgreesWithSheetTest.kt` — seed one week with mixed activity and two never-opened days, run `GetWeekSummary` (which backfills) and `GetClosedWeekSummary` (which does not) against the same seed, assert their `WeekSummary` figures are equal. This is SC-006 and the test most likely to fail; fix `GetClosedWeekSummary`'s projection, never the sheet.

### Summary branch in the decision functions

- [X] T047 [US1] Add weekly-summary cases to `BuildNotificationPlanTest.kt`: an anchor at `weekClosesAt` when the category is enabled; **no anchor when `weekClosesAt` is null** (any day but the week's Friday); none when dormant; none when already terminal in the ledger.
- [X] T048 [US1] Extend `BuildNotificationPlan.kt` with the `WEEKLY_SUMMARY` branch. Make T047 pass.
- [X] T049 [US1] Add weekly-summary cases to `EvaluateAnchorTest.kt`: inside quiet hours returns `Hold(quietHours.endAfter(now, zone))`, **not** `Discard`; outside quiet hours returns `Post`; dormant returns `Discard(SUMMARY_DORMANT)`; a `HELD` ledger row does not block a later `Post`; a `DELIVERED` row does.
- [X] T050 [US1] Extend `EvaluateAnchor.kt` with step 10 and the `WEEKLY_SUMMARY` half of step 11 from the contract. Make T049 pass.
- [ ] T051 [US1] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HeldSummaryTest.kt`. Cases: a summary held during quiet hours writes a `HELD` row with `heldUntil` set; re-running the worker at `heldUntil` posts it exactly once and flips the row to `DELIVERED`; a third run discards with `ALREADY_DELIVERED`; a quiet window covering a whole day still posts once at its end; **editing quiet hours while a summary is held does not post it twice** — widen the window, then narrow it, then reach the new end, and assert exactly one post in total (FR-035).
- [ ] T052 [US1] Implement the hold path in `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/NotificationWorker.kt`: on `Hold`, write the `HELD` row and schedule a second alarm at `until`. Make T051 pass.

### Weekly Summary screen

- [ ] T053 [P] [US1] Create `app/src/main/java/com/giraffe/mizanapp/weeklysummary/WeeklySummaryUiState.kt` exactly as specified in [contracts/ui-state.md](./contracts/ui-state.md) §2, including `WeeklySummaryContent.Waiting`, `.Closed` and `.Unavailable`. There must be no "current week" variant.
- [ ] T054 [P] [US1] Write `app/src/test/java/com/giraffe/mizanapp/weeklysummary/WeeklySummaryViewModelTest.kt`. Cases: no closed week yet → `Waiting` carrying the date the first summary arrives; one closed week → `Closed` with the right figures; a week with no completions → `Closed` with `quiet = true` and still fully populated; **a week only partly inside recorded history → `Closed` with a populated `coverage` note** (FR-029); a repository failure → `Unavailable`, never a zeroed `Closed`; navigating earlier and later respects `canGoEarlier`/`canGoLater` at the ends of recorded history; opening with an explicit `WeekKey` shows that week rather than the most recent.
- [ ] T055 [US1] Create `app/src/main/java/com/giraffe/mizanapp/weeklysummary/WeeklySummaryViewModel.kt` exposing one immutable `StateFlow<WeeklySummaryUiState>`. It accepts an optional `WeekKey`; null means the most recently closed week. No mutable state may be exposed. Make T054 pass.
- [ ] T056 [US1] Write `app/src/androidTest/kotlin/com/giraffe/mizanapp/WeeklySummaryScreenTest.kt`. Cases: the `Waiting` state renders its explanatory line and a control leading to the weekly sheet, and renders **no** figures; a `Closed` state renders days engaged, tasks recorded and points earned; a `quiet` week renders without any zero presented as a shortfall; the earlier/later controls are disabled at the ends of recorded history. Compose surfaces are not exempt from Principle I — only `@Preview` composables are.
- [ ] T057 [US1] Create `app/src/main/java/com/giraffe/mizanapp/weeklysummary/WeeklySummaryScreen.kt`. Use the design tokens in `CLAUDE.md` (background `#EFECE5`, primary `#0B5D42`, ink `#14211C`, muted `#5C6E66`). **No red anywhere, no ✗, no empty-ring-as-failure, no count of anything not done, and never render the difference between earned and available as its own figure.** Make T056 pass.
- [ ] T058 [US1] Add the destination to `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt`: `data class WeeklySummary(val week: WeekKey?)` in the sealed interface, with `encode` producing `WEEKLYSUMMARY` for a null key and `WEEKLYSUMMARY:<key>` otherwise, and `decode` reversing both — per [contracts/ui-state.md](./contracts/ui-state.md) §"Reaching the screen". Add the `when` branch in `AppRoute`. Add the in-app entry point as a row on `app/src/main/java/com/giraffe/mizanapp/week/WeekScreen.kt`, beside the existing link to Insights. **Do not add a navigation tab** — "Progress" is a design-document grouping, not a route.

### Deep link, permission and copy

- [ ] T059 [US1] Write `app/src/androidTest/kotlin/com/giraffe/mizanapp/NotificationDeepLinkTest.kt`. Cases: launching `MainActivity` with the extra `mizan.destination = "WEEKLYSUMMARY:2026-08-29"` opens the summary screen **showing that week, not the most recent**; `"WEEKLYSUMMARY"` alone opens the most recent; rotating the device does not re-navigate; launching with no extra opens Today.
- [ ] T060 [US1] Implement deep-link seeding in `AppRoute` in `MainActivity.kt`: read the `mizan.destination` extra once on first composition, decode it with the existing `decode` function, seed the stack, then clear the extra so a configuration change does not re-navigate. Make T059 pass.
- [ ] T061 [US1] Write `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationPermissionPromptTest.kt`. Cases: the prompt is not shown during the first week, whatever else is true; it is shown once the first week has closed and `permissionAskedAt` is null; it is never shown a second time once `permissionAskedAt` is set; dismissing it leaves the app fully usable and the summary screen fully populated.
- [ ] T062 [US1] Create `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationPermissionPrompt.kt` — dismissible and non-blocking, following the Phase 9 location prompt. Gate it on `permissionAskedAt` being null AND a first closed week existing (FR-007a). Make T061 pass.
- [ ] T063 [US1] Write `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationContentMapperTest.kt` for the summary category. Cases: the body names days engaged, tasks recorded and points earned; the mapper is a pure function of `NotificationContent` and reads no clock; a `quiet` week still produces a body, and it contains none of the words in the forbidden list from `CLAUDE.md`'s Principle IX section.
- [ ] T064 [US1] Create `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationContentMapper.kt` with the summary mapping — `NotificationContent` to title and body strings. Copy must report days engaged, tasks recorded and points earned. It must NOT count anything not done, must not compare to anyone, and must not present any figure as falling short (FR-025). Make T063 pass.
- [ ] T065 [US1] Register in `Modules.kt`: `factory { GetClosedWeekSummary(get(), get(), get(), get()) }` in `domainModule`, and `viewModel { (week: WeekKey?) -> WeeklySummaryViewModel(get(), week) }` in `appModule` following the existing parameterised `DaySummaryViewModel` line.

**Checkpoint**: US1 is independently demonstrable. A week closes, a summary is posted, and the screen shows closed weeks with a waiting state before the first one. Prayer nudges and the streak reminder do not exist yet.

---

## Phase 4: User Story 2 — A nudge arrives in the prayer window (Priority: P1)

**Goal**: One nudge per prayer window, skipped when the block is done, never scheduled without location.

**Independent Test**: With a fixed location, controllable clock and seeded day plan, advance across each of the five calculated prayer times and confirm exactly one nudge per window with outstanding tasks, none for a completed block, and none at all on the fallback regime.

- [X] T066 [P] [US2] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/PrayerSectionMapTest.kt`. Cases: each of `fajr`, `dhuhr`, `asr`, `maghrib`, `isha` maps to the matching `PrayerTimes` instant; each of `qiyam-witr`, `quran`, `adhkar`, `fasting`, `friday` maps to null; an unknown section id maps to null rather than throwing.
- [X] T067 [US2] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/PrayerSectionMap.kt`: `fun prayerInstantFor(sectionId: String, times: PrayerTimes): Instant?` and `fun nextPrayerAfter(sectionId: String, times: PrayerTimes): Instant?` (the next of the five, or the day's end for `isha`). Make T066 pass.
- [X] T068 [P] [US2] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/NudgeWindowTest.kt`. Cases: an anchor for a prayer at 15:30 fires at 15:50; the window ends at the next prayer instant; no anchor is produced when the offset would push it at or past the next prayer instant.
- [X] T069 [US2] Create `domain/src/main/kotlin/com/giraffe/mizanapp/domain/notification/NudgeWindow.kt`: `object NudgeWindow { val OFFSET_AFTER_PRAYER: Duration = Duration.ofMinutes(20) }` plus the containment helper. **A constant, never a setting** (FR-009, Principle VI). Make T068 pass.
- [X] T070 [US2] Add prayer cases to `BuildNotificationPlanTest.kt`: five anchors when all five sections are incomplete; four when one section is at its occurrence limits; **zero when `prayerTimes` is null** (the fallback regime, FR-016); zero when the category is disabled; each anchor's `windowEndsAt` equals the next prayer instant.
- [X] T071 [US2] Extend `BuildNotificationPlan.kt` with the `PRAYER_WINDOW` branch. When `prayerTimes` is null, produce nothing — do not approximate, extrapolate or borrow an instant from another date. Make T070 pass.
- [X] T072 [US2] Add prayer cases to `EvaluateAnchorTest.kt`: `now >= windowEndsAt` → `Discard(WINDOW_PASSED)`, using `>=` so an anchor firing exactly at the next prayer instant is discarded; section complete at fire time → `Discard(SECTION_COMPLETE)`; inside quiet hours → `Discard(QUIET_HOURS)` and never `Hold`; otherwise `Post`.
- [X] T073 [US2] Extend `EvaluateAnchor.kt` with steps 6, 7 and the non-summary half of step 11. Make T072 pass.
- [ ] T074 [P] [US2] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/NudgeYearTest.kt` — SC-007. Over a simulated year at a fixed mid-latitude location including both solstices, assert every nudge lands strictly after its own prayer instant and strictly before the following one, and that no day produces more than five prayer anchors.
- [ ] T075 [US2] Add nudge cases to `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationContentMapperTest.kt`: the body names the section and what remains available in it, and contains none of the forbidden Principle IX vocabulary.
- [ ] T076 [US2] Add the nudge mapping to `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationContentMapper.kt`. Copy must NOT count omissions, must not say "missed" or "forgot", and must not use any deficit framing (FR-015). Make T075 pass.
- [ ] T077 [US2] Add the section deep-link case to `app/src/androidTest/kotlin/com/giraffe/mizanapp/NotificationDeepLinkTest.kt`: the extra `TODAY:asr` opens Today on the Asr block.
- [ ] T078 [US2] Extend `encode`/`decode` in `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` so `Today` can carry a section id (`TODAY:asr`), and open the stepped flow on that block. Make T077 pass.
- [ ] T079 [US2] Write `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationReconcilerTest.kt`: recording the last outstanding task in a section withdraws that section's posted nudge; recording a task in a different section withdraws nothing; withdrawing a nudge that was never posted does not throw.
- [ ] T080 [US2] Create `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationReconciler.kt` collecting `CompletionRepository.observeCompletions(today)` in the application scope and calling `NotificationPresenter.withdraw` when a section reaches its occurrence limits. **Do not touch `CompletionRepository`'s write path** — the domain must never learn that notifications exist (research R5). Start it from `MizanApplication`. Make T079 pass.

**Checkpoint**: US2 works independently. Nudges arrive in their windows, vanish when the work is done, and never appear without location.

---

## Phase 5: User Story 3 — A protective reminder before the day ends (Priority: P2)

**Goal**: One streak reminder per day, only when there is a live streak and nothing recorded yet.

**Independent Test**: Advance to the at-risk instant under three conditions — live streak with nothing recorded, live streak with something recorded, and no streak — and confirm one, zero and zero notifications.

- [X] T081 [US3] Add streak cases to `BuildNotificationPlanTest.kt`: an anchor at `StreakClock.nextBoundaryAfter(now, boundary.expiresAt)` when `streak.current >= 1 && !streak.todayCounted`; no anchor when `todayCounted`; no anchor when `current == 0`; the anchor is produced on the fallback regime too, measured from that regime's own `expiresAt`.
- [X] T082 [US3] Extend `BuildNotificationPlan.kt` with the `STREAK_AT_RISK` branch. Read the offset from `StreakClock` — never restate four hours anywhere in this feature (Principle VII). Make T081 pass.
- [X] T083 [US3] Add streak cases to `EvaluateAnchorTest.kt`: `todayCounted` at fire time → `Discard(DAY_ALREADY_COUNTED)`; `current == 0` → `Discard(NO_LIVE_STREAK)`; inside quiet hours → `Discard(QUIET_HOURS)`; otherwise `Post`.
- [X] T084 [US3] Extend `EvaluateAnchor.kt` with steps 8 and 9. Make T083 pass.
- [ ] T085 [US3] Add streak cases to `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationContentMapperTest.kt`: the body is framed around continuing an established streak and contains none of "lose", "lost", "break", "expire", "don't", "last chance", or any countdown.
- [ ] T086 [US3] Add the streak mapping to `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationContentMapper.kt` — for example "One task keeps your 12-day streak going." It must NOT state a consequence of inaction (FR-021). Make T085 pass.
- [ ] T087 [US3] Add the streak case to `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationReconcilerTest.kt`: recording any task withdraws the posted streak reminder.
- [ ] T088 [US3] Extend `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationReconciler.kt` accordingly. Make T087 pass.
- [ ] T089 [US3] Add the streak deep-link case to `app/src/androidTest/kotlin/com/giraffe/mizanapp/NotificationDeepLinkTest.kt`: the reminder's destination opens Today.
- [ ] T090 [US3] Make T089 pass in `app/src/main/java/com/giraffe/mizanapp/MainActivity.kt` — the bare `TODAY` destination already exists, so this is wiring the anchor's `destination` field, not new routing.

**Checkpoint**: All three categories work. The app has never yet been silenceable from the UI — that is the next phase.

---

## Phase 6: User Story 4 — Turning it down, and turning it off (Priority: P2)

**Goal**: Per-category control, a master silence, quiet hours, and honest disclosure of every state.

**Independent Test**: Enable everything, switch each control off in turn, advance a simulated week, and confirm nothing is posted for a disabled category and that pending notifications are withdrawn the instant the switch flips.

- [ ] T091 [P] [US4] Write `app/src/test/java/com/giraffe/mizanapp/notifications/NotificationSettingsTest.kt`. Cases for `statements`, which must never be empty: permission not granted → a line saying so and how to reach system settings; `deliveryMode == DeliveryMode.RELAXED` → a line saying timing may drift; nudges on with the fallback regime → a line saying nudges need location; summary dormant → a line saying the weekly notification is paused, that the summary is still on its screen, and what resumes it; `allSilenced` → a line saying everything is silenced and the categories are remembered. Also assert `allSilenced = true` does **not** clear the three category booleans.
- [ ] T092 [US4] Create `app/src/main/java/com/giraffe/mizanapp/notifications/NotificationSettings.kt` with the data class and `PermissionState` enum from [contracts/ui-state.md](./contracts/ui-state.md) §1, plus the `statements` builder. Use the domain `QuietHours` and `DeliveryMode` types directly — there are no display-only wrappers. Make T091 pass.
- [ ] T093 [US4] Add `val notifications: NotificationSettings` to `ProfileUiState` in `app/src/main/java/com/giraffe/mizanapp/profile/ProfileUiState.kt`, and add the `NotificationSettingsEvent` cases to `ProfileEvent` exactly as listed in the contract. Add no event for editing a notification, a per-category time, or a per-task reminder.
- [ ] T094 [US4] Write `app/src/test/java/com/giraffe/mizanapp/profile/ProfileViewModelNotificationsTest.kt`: every settings event persists preferences and then calls `replaceAll` or `cancelAll` **in the same operation**; turning a category off withdraws its already-posted notifications immediately; `SetAllSilenced(true)` calls `cancelAll`; `SetAllSilenced(false)` restores exactly the categories that were enabled before.
- [ ] T095 [US4] Extend `app/src/main/java/com/giraffe/mizanapp/profile/ProfileViewModel.kt` to handle the new events. FR-005 requires withdrawal at the moment the switch flips, not at the next refresh. Make T094 pass.
- [ ] T096 [US4] Write `app/src/androidTest/kotlin/com/giraffe/mizanapp/ProfileNotificationSectionTest.kt`: the three category switches and the master silence switch render and reflect state; every line in `statements` is displayed; the quiet-hours row shows the window when set and an off state when not; no red or warning iconography appears anywhere in the section.
- [ ] T097 [US4] Extend `app/src/main/java/com/giraffe/mizanapp/profile/ProfileScreen.kt` with the notifications section: three category switches, a master silence switch, a quiet-hours row with start and end pickers and a clear control, and every line from `statements`. Place it beside the existing location section, same design tokens. Make T096 pass.
- [ ] T098 [P] [US4] Write `app/src/test/java/com/giraffe/mizanapp/notifications/QuietHoursSettingsTest.kt`: setting a window that crosses midnight round-trips; clearing it returns to null; the window is interpreted in device-local time.
- [ ] T099 [US4] Implement the quiet-hours edit path end to end: the picker in `ProfileScreen.kt`, `SetQuietHours` handling in `ProfileViewModel.kt`, persistence through `data/src/main/kotlin/com/giraffe/mizanapp/data/notification/NotificationPreferencesStore.kt`, then `replaceAll`. Make T098 pass.
- [ ] T100 [US4] Write `app/src/test/java/com/giraffe/mizanapp/notifications/ExactAlarmPermissionTest.kt`: the exact-delivery request is never issued while notification permission is absent; it is issued once notification permission is granted and exact delivery is unavailable; it is not issued when exact delivery is already available.
- [ ] T101 [US4] Create `app/src/main/java/com/giraffe/mizanapp/notifications/ExactAlarmPermission.kt` launching `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` under those conditions (FR-007b). Make T100 pass.
- [ ] T102 [US4] Write `app/src/androidTest/kotlin/com/giraffe/mizanapp/PermissionDeniedTest.kt`: with permission denied and every category on, nothing is posted, the settings surface states it, the summary screen is fully populated, and nothing crashes (FR-007, FR-027).

**Checkpoint**: Every functional requirement is implemented. What remains is verification.

---

## Phase 7: Polish and Cross-Cutting Verification

- [ ] T103 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/DailyNotificationCapTest.kt` — SC-010. No accountability day produces more than seven notifications, counting a held summary against the day its anchor belonged to rather than the day it is finally posted (FR-042); and the count falls to zero as the day's tasks are recorded.
- [ ] T104 [P] Write `domain/src/test/kotlin/com/giraffe/mizanapp/domain/notification/DormantUserTest.kt` — SC-014. Three simulated months with nothing recorded and every category at its default yields exactly two notifications, then none; recording one task makes the next week close post again.
- [ ] T105 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/DeliveryIdempotencyTest.kt` — SC-013. Post an anchor, then in turn: simulate a reboot, move the clock backwards, change the time zone, and re-derive the schedule repeatedly. Assert exactly one post in every case.
- [ ] T106 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/RelaxedDeliveryTest.kt` — SC-007a. In relaxed mode, deliver an anchor a simulated hour late and assert it is discarded `WINDOW_PASSED` and never posted in the following window.
- [ ] T107 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/HistoryUntouchedTest.kt` — SC-012. Seed a week of day plans and completions, snapshot every row of `day_plans` and `completions`, run a simulated week of full notification activity (all three categories, several posts, a held summary, a reboot, a prune), then snapshot again and assert the two snapshots are identical column for column. This is the strongest statement of FR-044 available.
- [ ] T108 [P] Write `data/src/androidTest/kotlin/com/giraffe/mizanapp/data/NotificationOfflineTest.kt` — FR-036 and FR-045. Two cases: with no network available, scheduling, evaluating, posting and withdrawing all succeed unchanged; and deleting every row of `notification_deliveries` changes no figure the app reports — only, at worst, one repeated notification.
- [ ] T109 Write `app/src/androidTest/kotlin/com/giraffe/mizanapp/NotificationCopyReviewTest.kt` — SC-009, **gating**. Collect every user-visible string this feature adds (notification titles and bodies, settings labels, every line of the summary screen) and assert none contains loss, failure, deficit, warning or comparative vocabulary. Then read all of them by hand against the Principle IX list in `CLAUDE.md`. The fixture catches vocabulary; a person catches tone. Both are required.
- [ ] T110 [P] Run the three negative checks in [quickstart.md](./quickstart.md) §"What this increment must not have done": `:domain`'s build file unchanged, no notification import in `domain/repository/`, no new direct clock or zone read anywhere.
- [ ] T111 Run the full suite: `./gradlew :domain:test :app:test :data:connectedAndroidTest :app:connectedAndroidTest`. All four green before the pull request opens.
- [ ] T112 Perform the four by-hand device checks in [quickstart.md](./quickstart.md) §"By hand, on a device, before merge" and record each result in the pull request description: a real nudge appearing at the right minute; exact-alarm refusal degrading as specified; reboot survival; and a fresh install in airplane mode requesting neither the notification nor the exact-delivery permission during its first week.
- [ ] T113 Update the Phase 10 section of `docs/PLAN.md` with a **Delivered** subsection in the style of Phase 8 and 9 — what shipped, the five clarification decisions that shaped it, the research R4 finding that the weekly summary must not reuse `GetWeekSummary` because it backfills, and the two design corrections `/speckit-analyze` forced (the `NotificationPlan` return type and the single `weekCloseInstant` derivation).
- [ ] T114 Open the pull request into `develop-v1`. Confirm before requesting merge: the Constitution Check in [plan.md](./plan.md) still passes; every test commit demonstrably precedes its implementation commit; the Principle III migration test (T018) and the history-untouched test (T107) are both present and green; and the exported Room schema for version 6 is committed.

---

## Dependencies

```text
Phase 1 (Setup)
   ↓
Phase 2 (Foundational) ── BLOCKS EVERYTHING BELOW
   ↓
   ├── Phase 3 (US1, P1) ── MVP; deliverable on its own
   ├── Phase 4 (US2, P1) ── needs Phase 2 only; independent of US1
   ├── Phase 5 (US3, P2) ── needs Phase 2 only; independent of US1 and US2
   └── Phase 6 (US4, P2) ── needs at least one category to exist, so start it after any one of US1–US3
          ↓
      Phase 7 (Polish) ── needs all of the above
```

**Within a story**, the order is always: test task → implementation task → next test task. Never
reorder these; the pull request's commit history is what the merge gate reads.

**Shared files that force sequencing.** Tasks touching the same file cannot run in parallel:

| File | Touched by |
|---|---|
| `domain/notification/BuildNotificationPlan.kt` | T028, T048, T071, T082 |
| `domain/notification/EvaluateAnchor.kt` | T030, T050, T073, T084 |
| `domain/.../BuildNotificationPlanTest.kt` | T027, T047, T070, T081 |
| `domain/.../EvaluateAnchorTest.kt` | T029, T049, T072, T083 |
| `data/notification/NotificationWorker.kt` | T040, T052 |
| `app/notifications/NotificationContentMapper.kt` | T064, T076, T086 |
| `app/.../NotificationContentMapperTest.kt` | T063, T075, T085 |
| `app/.../NotificationDeepLinkTest.kt` | T059, T077, T089 |
| `app/notifications/NotificationReconciler.kt` | T080, T088 |
| `app/MainActivity.kt` | T058, T060, T078, T090 |
| `app/di/Modules.kt` | T041, T065 |

## Parallel opportunities

- **Phase 2**: T002–T004 together; then T005/T009/T012/T014 together; then T007/T008/T011 together; then T016/T017 together; then T019/T020 together.
- **Phase 3**: T042 and T044 together; T053 and T054 together.
- **Phase 4**: T066, T068 and T074 together.
- **Phase 6**: T091 and T098 together.
- **Phase 7**: T103–T108 and T110 all together.
- **Across stories**: once Phase 2 is green, US1, US2 and US3 proceed independently — but they extend the same two functions and the same two test files, so coordinate commits on those four.

## Implementation strategy

**MVP is Phase 1 + Phase 2 + Phase 3 (US1).** That ships the default-on category, the summary screen
and the whole scheduling spine. It is genuinely useful alone: a person gets one honest summary a week
and a screen that remembers every closed week.

Then US2 (nudges), then US3 (streak reminder), then US4 (control). Each is a working increment. US4
last is deliberate — there is nothing to switch off until something can be switched on — but it must
ship in the same release as the categories, because "fully disableable" is a Principle IX obligation
and not a follow-up.

**If you have to stop early**, stop after a checkpoint, never mid-story. Every checkpoint leaves the
app in a state that builds, passes, and behaves correctly.

## The five ways this feature can go wrong

Re-read these before each phase.

1. **Writing history from a background worker.** `GetWeekSummary` calls `ensurePlanFor`, which writes
   day plans. Never call it from this feature. T045 exists to prevent it; T107 proves it (research R4).
2. **A second clock, location, prayer-time or week-close source.** Everything is a parameter or an
   injected provider, and `weekCloseInstant` is the only derivation of a week's close. T110 checks this
   mechanically; the review checks it properly.
3. **Shame in the copy.** "Missed", "lost", "failed", "you didn't", a count of omissions, a red
   anything. T109 is gating for exactly this reason.
4. **A notification in the wrong window.** Use `>=` in the staleness check, and always re-evaluate at
   fire time rather than trusting what was true at schedule time.
5. **Double posting.** The `anchorKey` primary key is the guarantee, not application logic. If you find
   yourself writing a "have we already sent this?" check outside the ledger, stop.
