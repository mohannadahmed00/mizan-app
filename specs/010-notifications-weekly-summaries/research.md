# Phase 0 Research: Notifications and Weekly Summaries

**Feature**: `specs/010-notifications-weekly-summaries` | **Date**: 2026-09-04

Ten questions the Technical Context could not answer on its own. Each records the decision, why, and
what was rejected. No `NEEDS CLARIFICATION` remains after this document.

---

## R1 — How is an exact-instant notification scheduled on Android 24–36?

**Decision**: `AlarmManager.setExactAndAllowWhileIdle` with a `PendingIntent` per anchor, guarded by
`canScheduleExactAlarms()`. Request the `SCHEDULE_EXACT_ALARM` permission via
`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Where it is unavailable, fall back to
`setAndAllowWhileIdle` — the inexact variant — and rely on `EvaluateAnchor`'s staleness rules to
discard anything that arrives in the wrong window.

**Rationale**: The spec's clarification chose exact delivery for all three categories (FR-036a). On
this project's `targetSdk 36`, `SCHEDULE_EXACT_ALARM` is not pre-granted; the app must ask, and the
person can refuse — which is precisely the shape FR-036b already anticipates. `AllowWhileIdle` is
required for both variants or Doze defers delivery indefinitely on an idle phone, which would defeat
the whole point of choosing exact.

`USE_EXACT_ALARM` (API 33+) is granted at install with no prompt, and was rejected: Play policy
restricts it to apps whose *core* function is an alarm clock or calendar. Mizan is neither, and
shipping it would be a policy violation dressed up as a convenience.

**Alternatives considered**:
- *WorkManager for everything.* Already in the project and needs no permission, but its minimum
  periodic interval is fifteen minutes and its one-shot delivery is explicitly best-effort. A Dhuhr
  nudge could land in the Asr window. Rejected against FR-036a; retained for the work the alarm
  triggers (R2).
- *A foreground service holding its own timer.* Precise and permission-light, but a persistent
  notification for an app that aims to send at most seven a day is absurd, and it burns battery for
  a feature that is idle 99% of the time.
- *`setAlarmClock`.* Exact and Doze-exempt, but it surfaces a system alarm icon and is semantically a
  user-set alarm. Misrepresenting a nudge as an alarm clock is a lie to the system UI.

---

## R2 — What runs at fire time, given a broadcast receiver's budget?

**Decision**: `NotificationTriggerReceiver` receives the alarm and immediately enqueues an expedited
one-shot `NotificationWorker` keyed uniquely per anchor. The worker loads the day plan, completions,
streak summary, preferences and delivery ledger, calls `EvaluateAnchor`, posts or discards, records
the outcome, and schedules the next anchor.

**Rationale**: A `BroadcastReceiver` gets roughly ten seconds on the main thread and may not touch
Room there. The project already runs `SyncWorker` through Koin's WorkManager factory with
auto-init disabled in the manifest, so the wiring, the expedited-work fallback policy and the test
harness (`androidx.work.testing`) all exist and are proven. Adding a second worker costs nothing new.

**Alternatives considered**:
- *`goAsync()` in the receiver.* Buys about thirty seconds and avoids a worker, but the process can
  still be killed mid-flight with no retry, and the work is not observable in tests the way a
  `WorkRequest` is.
- *Posting the notification directly from the receiver with pre-computed content.* Fastest, but it
  makes "never fire for an already-completed block" impossible — the decision has to read the record
  at fire time, not at schedule time. Rejected against FR-010 and FR-012.

---

## R3 — Where do preferences and delivery bookkeeping live?

**Decision**: Room, in the existing `MizanDatabase`, via an additive `MIGRATION_5_6` adding
`notification_preferences` (single row, `id = 0`) and `notification_deliveries` (one row per anchor).
Both device-local and non-synchronisable, following the `boundary_state` precedent.

**Rationale**: Phase 9 settled this exact question for device-local state and chose Room, single-row,
deliberately not synchronisable. Repeating that choice keeps one persistence mechanism in the app;
introducing DataStore alongside Room would give device-local settings a second home, which is the
kind of drift Principle VII objects to in the time domain and Principle VIII objects to generally.

The migration is additive, touches no history table, and is covered by a migration test asserting
that a seeded `day_plans` and `completions` row survive it byte for byte — the Principle III test the
merge gate requires of any increment touching persistence.

**Correction worth recording**: the `/speckit-clarify` summary said this increment "adds no Room
migration". That was about the *weekly figures*, which FR-024a forbids storing, and it overstated the
case. Preferences and delivery bookkeeping do need a table, and FR-045 always said so.

**Alternatives considered**:
- *DataStore for preferences, Room for deliveries.* Idiomatic for settings, but splits five values
  across two mechanisms and two test harnesses for no gain.
- *`SharedPreferences`.* No migration, but no observability without a listener shim, and the
  delivery ledger is a table by nature.

---

## R4 — How does a closed week get its figures without writing history?

**Decision**: A new read-only `GetClosedWeekSummary`. It reads stored plans and live completions for
the week, projects available points for any elapsed date with no stored plan using
`projectAvailablePoints` against `catalogue.versionEffectiveOn(date)`, and hands all of it to the
existing `buildWeekSummary`. It never calls `ensurePlanFor`.

**Rationale**: **This is the finding that changed the design.** The obvious move — reuse
`GetWeekSummary` — backfills: it calls `plans.ensurePlanFor(date)` for elapsed unopened days, which
*writes day plans*. Running that from a background worker at Friday Maghrib would have this feature
writing recorded history behind the person's back, violating FR-044 and Principle III, and it would
have been invisible in review because the call is one line deep inside an existing use case.

Projecting per-date with `versionEffectiveOn(date)` — rather than with the current version — is what
keeps SC-006 true. Backfill prices a day at the version effective *for that date*; projecting at the
current version would make the summary and the weekly sheet disagree the moment the catalogue
changes. The two paths must feed `buildWeekSummary` the same numbers, and a test asserts they do.

**Alternatives considered**:
- *Let the summary backfill.* Rejected outright — Principle III admits no exceptions.
- *Report only stored days and omit unopened ones.* Honest but wrong: a week where the person opened
  the app twice would report a denominator of two days, and the sheet would report seven.

---

## R5 — How is a posted notification withdrawn when the work gets done?

**Decision**: A `NotificationReconciler` in `:app`, collecting the completions flow for the current
accountability date within the application scope, cancelling any posted nudge whose section has
become complete and the streak reminder once the day is counted.

**Rationale**: A completion can only be recorded from the app's own UI, so the process is alive at the
exact moment a withdrawal becomes due. That makes FR-011 and FR-020 satisfiable without waking
anything up and without a second write path. Crucially it keeps the cancellation *out* of
`CompletionRepository`: the domain must not learn that notifications exist, and a repository that
posts UI side effects is the coupling Principle II exists to prevent.

**Alternatives considered**:
- *Cancel from the completion write path.* One line, and it drags the notification system into the
  domain's most-used function.
- *Re-evaluate on a timer.* Wakes the device to discover nothing changed.

---

## R6 — Where do the nudge offset and the section-to-prayer mapping live?

**Decision**: `NudgeWindow.OFFSET_AFTER_PRAYER = Duration.ofMinutes(20)` and a `PrayerSectionMap`
covering exactly the five section ids `fajr`, `dhuhr`, `asr`, `maghrib`, `isha`, both in
`:domain/notification/`. Anything else the catalogue contains maps to nothing and is silently not
nudged.

**Rationale**: Twenty minutes places the nudge after the obligatory prayer rather than during it. It
is a constant, not a setting (FR-009, Principle VI), and it sits beside `StreakClock` — the existing
precedent for "a timing rule that is a constant because settings are out of scope". The mapping is
total-by-omission rather than exhaustive so that a future catalogue section (Ramadan, Ashura, already
reserved by decision 7 in `docs/PLAN.md`) cannot accidentally acquire a nudge by being added.

**Alternatives considered**:
- *Derive the mapping from section order.* Breaks the moment a section is inserted.
- *Put the offset in the catalogue.* Makes timing administrator content, which invites it to become
  user content later. Rejected.

---

## R7 — How does tapping a notification reach the right screen?

**Decision**: The `PendingIntent` targets `MainActivity` with a `mizan.destination` string extra
encoded by the existing `encode`/`decode` pair. `AppRoute` seeds its `rememberSaveable` stack from
the extra on first composition, then clears it so a configuration change does not re-navigate.

**Rationale**: `MainActivity` already encodes its whole `Destination` stack to a single `String` for
`rememberSaveable`, including the `DAY:<iso-date>` form. A notification deep-link is the same problem
already solved, so it reuses the same codec rather than introducing a parallel route syntax — or a
navigation library, whose deferral (`002` research R3) still holds.

**Alternatives considered**:
- *Adopt Navigation Compose for deep links.* A library, a route DSL and a migration of six existing
  destinations, to route three notifications.
- *A second activity per category.* Three launch surfaces, three back-stack behaviours.

---

## R8 — What re-establishes schedules after reboot, update, or a time change?

**Decision**: One `SystemEventReceiver` registered for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
`TIMEZONE_CHANGED` and `TIME_SET`. It enqueues the same worker, which refreshes `BoundaryStatus` and
re-derives the full schedule from `buildNotificationPlan`.

**Rationale**: Alarms do not survive reboot or app replacement, and a timezone change invalidates
every anchor. Re-deriving the whole plan from scratch — rather than patching individual alarms — is
what makes FR-041 (at most once per anchor) achievable: the delivery ledger is the memory, the
schedule is disposable, and rebuilding it repeatedly is idempotent by construction. `TIME_SET` also
covers the backwards-clock edge case, where the ledger is what prevents a second delivery.

**Alternatives considered**:
- *Persist each alarm and restore individually.* More state, same result, more ways to drift.
- *Rely on WorkManager's automatic reboot persistence.* Only applies to work, not to alarms, and
  R1 already rejected work as the delivery mechanism.

---

## R9 — How is the held weekly summary implemented across quiet hours and reboots?

**Decision**: The summary's delivery row is written in a `HELD` state carrying the quiet-hours end
instant, and a second alarm is scheduled for that instant. Delivery flips the row to `DELIVERED`.
Both transitions go through `DeliveryLedger`, which is a pure function over rows.

**Rationale**: FR-035 requires the held summary to be delivered exactly once, to survive a quiet
window that covers the whole day, and not to double-deliver if quiet hours are edited while it is
held. Holding the state in the same table that answers "already delivered?" means one source of truth
answers both questions, and a reboot mid-hold loses nothing.

**Alternatives considered**:
- *Keep the held summary in memory.* Lost on process death, which is likely across a night.
- *Re-check on next app open.* Delivers the summary when the person is already looking at the app,
  which is the one moment a notification is pointless.

---

## R10 — What is testable where?

**Decision**:

| Layer | Covers | Harness |
|---|---|---|
| `:domain` unit | `buildNotificationPlan`, `EvaluateAnchor`, quiet hours across midnight, nudge windows over a simulated year, dormancy fold, `GetClosedWeekSummary` over fixtures | JUnit4 + `kotlinx-coroutines-test`, literals and a fake clock |
| `:data` instrumentation | `MIGRATION_5_6` non-destructiveness, delivery-ledger idempotency across simulated reboot and backwards clock, scheduler exact/inexact selection | `MigrationTestHelper`, `androidx.work.testing` |
| `:app` unit | Settings state mapping, permission-absent states, summary UI state including the waiting state | JUnit4 |
| `:app` instrumentation | Deep-link stack seeding, the Principle IX string review fixture (SC-009) | `ui-test-junit4` |

**Rationale**: The pure-function design pushes almost everything into millisecond unit tests, which is
what makes Principle I affordable here. The three things that genuinely need a device — the
migration, alarm scheduling, and deep-link routing — are exactly the three that cannot be faked
honestly.

**Deliberately not automated, and flagged now rather than at merge**: that a notification actually
appears in the system tray at the right minute on a real device, and that exact-alarm permission
behaves as expected when refused. Both need a physical device and a wall clock. They belong in
`quickstart.md` as by-hand validation, in the same way spec 007 recorded its five deferred manual
checks rather than pretending an automated test covered them.
