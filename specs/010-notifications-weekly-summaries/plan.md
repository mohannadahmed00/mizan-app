# Implementation Plan: Notifications and Weekly Summaries

**Branch**: `spec/010-notifications-weekly-summaries` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-notifications-weekly-summaries/spec.md`

## Summary

Three local notification categories — a nudge per prayer window, one streak reminder per day, one
summary per closed week — plus a read-only Weekly Summary screen. Everything is scheduled on the
device against instants the Phase 9 provider already calculates; nothing is pushed, nothing is
fetched, and nothing is written to recorded history.

The technical approach turns on one decision: **the whole feature is a pure function.** A single
`buildNotificationPlan` in `:domain` takes the resolved boundary state, the day's prayer instants,
the day's plan and completions, the streak summary, the person's preferences, and the record of what
has already been delivered — and returns the anchors to schedule and, at fire time, the single verdict
*post this* or *discard this*. Android supplies instants and delivers broadcasts; it makes no
decisions. That is what makes the five-notifications-a-day behaviour, the staleness rules, quiet
hours, and two-empty-week dormancy testable with literals and a fake clock, and it is what keeps
`:domain` free of the platform.

Around that function: an `AlarmManager` scheduler and a boot/timezone receiver in `:data`, an
additive Room migration for two device-local tables (preferences and delivery bookkeeping — never
figures), a settings section on the existing profile screen, and one new destination.

## Technical Context

**Language/Version**: Kotlin 2.2.10, JVM target 11, core-library desugaring for `java.time`

**Primary Dependencies**: No new library. Platform `AlarmManager` and `NotificationManagerCompat`;
WorkManager 2.10.1, Room 2.8.1, Koin 4.1.0, Compose BOM 2025.06.01 — all already present. The Phase 9
`PrayerTimesProvider` / `BoundaryStatus` and the Phase 4 `StreakClock` are consumed as they stand.

**Storage**: Room, additive migration 5 → 6. Two device-local, deliberately non-synchronisable tables:
`notification_preferences` (single row) and `notification_deliveries` (one row per delivered or held
anchor). No table holds a computed figure.

**Testing**: JUnit4 + `kotlinx-coroutines-test` for `:domain` and `:app` unit tests; Room
`MigrationTestHelper` and instrumentation in `:data`; Compose `ui-test-junit4` in `:app`.

**Target Platform**: Android, minSdk 24, compileSdk/targetSdk 36

**Project Type**: Mobile — Android multi-module (`:app` → `:data` → `:domain`)

**Performance Goals**: `TimeProvider.today()` stays a synchronous in-memory field read, untouched by
this feature. Fire-time evaluation runs off the main thread and completes well inside a broadcast
receiver's budget by handing off to a worker (research R2).

**Constraints**: Offline on every path (Principle IV). No network call may appear in scheduling,
evaluation, posting or withdrawal. At most seven notifications per accountability day; zero on a
fully-recorded day.

**Scale/Scope**: 3 categories, ≤7 anchors per day, 1 new destination, 1 settings section, 1 Room
migration, 2 new tables, ~8 pure domain rules.

## Constitution Check

*GATE: passes before Phase 0 research. Re-checked after Phase 1 design — see the second table.*

Every principle this increment touches, and how the plan complies.

| Principle | Touched? | How this plan complies |
|---|---|---|
| **I. Test-First** (non-negotiable) | Yes | Every rule this feature adds is a pure function in `:domain` — the nudge window, the quiet-hours predicate, the dormancy fold, the week-close derivation, the closed-week aggregation, and `buildNotificationPlan` itself. Each gets its failing test first, then `:data` (migration, stores, scheduler, presenter, receivers, worker), then `:app` UI. **Every production file gets a preceding test, with no exceptions beyond the three the constitution itself names** — DI wiring, `@Preview` composables, and generated code. That includes the mappers (`DeliveryStore`, `NotificationContentMapper`), the platform adapters, the two broadcast receivers, the worker, and every Compose surface, each of which gets a screen-level test rather than leaning on its ViewModel's. The PR's commit history is the evidence the merge gate reads. |
| **II. Domain Purity** | Yes | `:domain` gains rules only — no `AlarmManager`, no `NotificationManager`, no `Context`. The platform is reached through two new interfaces declared in `:domain` and implemented in `:data` (`NotificationScheduler`, `NotificationPresenter`), exactly as Phase 9 did with `PrayerTimesProvider`. |
| **III. Immutable History** (non-negotiable) | Yes | FR-044: this feature performs no write to `day_plans` or `completions`. That is enforced structurally — the closed-week read path takes `DayPlanRepository` and `CompletionRepository` but never calls `ensurePlanFor`, which is why it cannot reuse `GetWeekSummary` (research R4). The required immutability test changes catalogue points and schedules and asserts a closed week's summary is unmoved. |
| **IV. Offline-First** | Yes — **and under real tension** | No network on any path, and the app stays fully usable when every permission is refused. But this feature adds a *third* refusable platform permission (exact delivery) on top of notifications and Phase 9's location, and each refusal degrades something. FR-036b defines the degradation explicitly — relaxed delivery plus the staleness rules, never silence — and FR-007a keeps the first week prompt-free. Addressed directly rather than as a routine pass, per Principle VII's recorded tension clause. |
| **V. Backend Independence & Sync Readiness** | Yes | Both new tables are device-local and deliberately not synchronisable (FR-043), following the `boundary_state` precedent from Phase 9: quiet hours are a property of *this* phone, and a delivery record from one device must never suppress a notification on another. Principle V governs synchronisable rows; these are declared non-synchronisable in the data model, so they carry no `userId`, no tombstone and no `updatedAt`, and the sync engine is not extended. |
| **VI. Fixed Content, No User Authoring** | Yes | Control is per category only (FR-006). No per-task reminder, no custom time, no reordering. The nudge offset is a constant in `:domain`, not a setting (FR-009). Quiet hours are the one time value the person sets, and they govern *silence*, not content. |
| **VII. Deterministic Time** (and location) | Yes — **the principle most at risk here** | No second provider (FR-039). Prayer instants come only from `PrayerTimesProvider`; the day's end and the week's close come only from `BoundaryStatus` and the existing `WeekBoundary`; the at-risk offset is read from `StreakClock`, not restated. The nudge offset and the dormancy threshold each live in exactly one place. Fire-time evaluation is passed `now` as a parameter rather than reading a clock, the same discipline `BoundaryStateStore.refresh` already follows. |
| **VIII. Vertical Slices** | Yes | One coherent capability, shipped whole. No abstraction is introduced for a category that does not exist, no configuration surface beyond the three toggles and quiet hours, and no table for a future feature. The summary screen shows closed weeks and nothing else (FR-027a). |
| **IX. Encouragement, Never Shame** | Yes — **the reason this increment is risky** | This is the first increment that speaks unprompted, where copy is read hours later out of context. Three structural defences: a fresh install is quiet by default (FR-003); no category can report a count of anything not done (FR-015, FR-021, FR-025); and the summary goes dormant after two empty weeks (FR-030a) so a lapsed user is never told weekly that nothing happened. SC-009 is a gating review of every string this feature adds, run against the CLAUDE.md Principle IX list before merge. |

**Gate result: PASS.** No violation to justify; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/010-notifications-weekly-summaries/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── notification-plan.md
│   ├── platform-ports.md
│   └── ui-state.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # /speckit-tasks output — NOT created here
```

### Source Code (repository root)

```text
domain/src/main/kotlin/com/giraffe/mizanapp/domain/
├── notification/
│   ├── NotificationCategory.kt      # the three fixed kinds
│   ├── NotificationPreferences.kt   # enablement, master silence, quiet hours
│   ├── QuietHours.kt                # window predicate; crosses midnight
│   ├── NudgeWindow.kt               # the offset constant + window containment rule
│   ├── PrayerSectionMap.kt          # section id -> prayer instant, five only
│   ├── NotificationAnchor.kt        # category + instant + the date/week it speaks for
│   ├── WeekCloseInstant.kt          # the one place weekClosesAt is derived
│   ├── DeliveryLedger.kt            # "already posted for this anchor?" + held summary
│   ├── BuildNotificationPlan.kt     # THE pure function: NotificationPlan(anchors, refreshAt)
│   ├── EvaluateAnchor.kt            # THE pure function: post or discard, at fire time
│   ├── NotificationScheduler.kt     # port, implemented in :data
│   └── NotificationPresenter.kt     # port, implemented in :data
├── week/
│   └── SummaryDormancy.kt           # two-consecutive-empty-weeks fold
└── usecase/
    └── GetClosedWeekSummary.kt      # read-only; never backfills (research R4)

data/src/main/kotlin/com/giraffe/mizanapp/data/
├── db/entities/NotificationPreferencesEntity.kt
├── db/entities/NotificationDeliveryEntity.kt
├── db/daos/NotificationDao.kt
├── db/MizanDatabase.kt              # version 5 -> 6, MIGRATION_5_6 (additive)
└── notification/
    ├── NotificationPreferencesStore.kt
    ├── DeliveryStore.kt                # ledger reads/writes/prune; pure mapping
    ├── AlarmNotificationScheduler.kt   # AlarmManager; exact with a stated fallback
    ├── AndroidNotificationPresenter.kt # channels, post, withdraw
    ├── NotificationTriggerReceiver.kt  # broadcast -> enqueue worker
    ├── SystemEventReceiver.kt          # BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME/TIMEZONE
    └── NotificationWorker.kt           # evaluates, posts or discards, schedules next

app/src/main/java/com/giraffe/mizanapp/
├── notifications/
│   ├── NotificationSettings.kt         # the UI state block, folded into ProfileUiState
│   ├── NotificationContentMapper.kt    # NotificationContent -> title/body strings
│   ├── NotificationPermissionPrompt.kt # non-blocking, never in week one
│   ├── ExactAlarmPermission.kt         # never asked before notification permission
│   └── NotificationReconciler.kt       # withdraws on completion, while the app lives
├── weeklysummary/
│   ├── WeeklySummaryScreen.kt
│   ├── WeeklySummaryUiState.kt
│   └── WeeklySummaryViewModel.kt
├── profile/ProfileUiState.kt           # += NotificationSettings
├── profile/ProfileScreen.kt            # += the notifications section
├── week/WeekScreen.kt                  # += the entry point to the summary screen
├── MainActivity.kt                     # += Destination.WeeklySummary(week), intent deep-link
└── di/Modules.kt                       # Koin wiring
```

**Structure Decision**: The existing three-module layout is unchanged and no module is added. The
split follows the same shape Phase 9 used and for the same reason: every decision lives in `:domain`
as a pure function over values, `:data` owns the platform mechanism, and `:app` owns presentation.
The two ports (`NotificationScheduler`, `NotificationPresenter`) exist because something is being
substituted *now* — the tests substitute them — not speculatively (Principle VIII).

The Weekly Summary screen joins the hand-rolled `Destination` stack in `MainActivity` rather than
introducing a navigation library; the deferral recorded in `002` research R3 and revisited in `005`
still holds, and a notification deep-link seeds the stack from an intent extra rather than needing a
route parser (research R7). It is reached in-app from `WeekScreen`, beside the existing link to
Insights — the product design's "Progress" grouping is a design-document idea, not a route, and this
feature adds no tab.

### Two design corrections made after the first analysis pass

Both were found by `/speckit-analyze` before any code existed, and both are recorded because the
first version of this plan was wrong about them:

- **`buildNotificationPlan` returns `NotificationPlan(anchors, refreshAt)`, not a bare list.** The app
  must wake at each day's end even when everything is silenced, or nothing rebuilds the schedule and a
  week close is missed on a phone that is never opened. The first draft tried to express that wake as
  a fourth kind of anchor, which is untypable: `NotificationAnchor.category` is a `NotificationCategory`,
  and FR-001 fixes that enum at three values, each of which names something the person sees.
- **`weekClosesAt` has exactly one derivation, in `WeekCloseInstant.kt`.** The first draft made it a
  parameter and then never said who computes it — which, on a project whose Principle VII exists
  precisely to stop two places disagreeing about when a week ends, was the most dangerous omission in
  the plan. It returns null rather than projecting a future Maghrib, because projecting one would be a
  boundary calculation outside the boundary provider.

## Complexity Tracking

No Constitution Check violations. Nothing to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(none)* | — | — |

## Post-Design Constitution Re-Check

Re-run after Phase 1. The design changed nothing about the gate result, but three things are worth
recording because the design is what makes them true rather than merely intended:

- **Principle III is now structural, not procedural.** `GetClosedWeekSummary` takes no
  `CatalogueRepository` write path and never calls `ensurePlanFor`; research R4 records why reusing
  `GetWeekSummary` — which backfills, and therefore writes — would have violated FR-044 silently from
  a background worker. This was the design's most dangerous near-miss.
- **Principle VII survived the fire-time path.** `EvaluateAnchor` takes `now`, the boundary state and
  the prayer instants as parameters. Nothing in `:data`'s receiver or worker reads a clock or a
  location; they pass through what the providers already resolved.
- **Principle V's shape is declared, not defaulted.** Both new tables are marked non-synchronisable in
  `data-model.md` with the reason stated per table, so a later reader cannot mistake the absent
  `userId` and tombstone for an oversight.

**Re-check result: PASS.**
