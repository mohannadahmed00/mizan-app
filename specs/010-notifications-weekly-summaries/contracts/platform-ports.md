# Contract: Platform Ports

**Feature**: `specs/010-notifications-weekly-summaries`

What `:data` must provide, and the platform behaviour each implementation is pinned to. The interfaces
themselves are declared in [notification-plan.md](./notification-plan.md); this file is the
obligations they carry.

---

## `AlarmNotificationScheduler` — `:data`

Implements `NotificationScheduler` over `AlarmManager`.

| Obligation | Source |
|---|---|
| One `PendingIntent` per anchor, keyed by `anchorKey`, so cancelling is exact | FR-005 |
| `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()`; `setAndAllowWhileIdle` otherwise | FR-036a, FR-036b, research R1 |
| `deliveryMode()` reports which is in force, so the settings surface can disclose drift | FR-036b |
| `replaceAll` cancels every previously scheduled anchor before scheduling the new set | research R8 |
| Never reads the clock to decide an instant — it schedules the instants it is handed | Principle VII |
| No network call on any path | FR-036, Principle IV |

**`USE_EXACT_ALARM` must not appear in the manifest.** It is granted at install with no prompt, and
Play policy restricts it to alarm-clock and calendar apps. Mizan is neither (research R1).

Permissions this adds to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`RECEIVE_BOOT_COMPLETED` is install-time and prompts nobody. The other two are the two refusable
permissions the spec accounts for; with Phase 9's `ACCESS_COARSE_LOCATION` that is three in total, each
independently refusable and each with a stated behaviour when refused.

---

## `AndroidNotificationPresenter` — `:data`

Implements `NotificationPresenter` over `NotificationManagerCompat`.

| Obligation | Source |
|---|---|
| One system channel per category, created on first use | plan.md Assumptions |
| `hasPermission()` reflects the live platform state, never a cached grant | FR-007 |
| `withdraw` is idempotent and safe for an anchor that was never posted | FR-011, FR-020 |
| Tapping opens `MainActivity` with the `mizan.destination` extra | FR-013, FR-030, research R7 |
| Category enablement in the app decides whether anything is *scheduled*; the system channel governs only how a posted notification *presents* | plan.md Assumptions |

A person who disables a channel in system settings therefore silences the presentation while the app
still schedules and evaluates. That is intentional: the app must not treat a system-level channel
change as a change to the person's in-app choice, or the two surfaces would fight over one value.

---

## `NotificationTriggerReceiver` — `:data`

Receives one alarm, enqueues `NotificationWorker` uniquely by `anchorKey`, returns. It performs no
database access and makes no decision (research R2).

## `SystemEventReceiver` — `:data`

Registered for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`. Enqueues a
schedule refresh. Same discipline: no decision, no clock read of its own.

| Event | Why it matters | Source |
|---|---|---|
| `BOOT_COMPLETED` | Alarms do not survive reboot | FR-037 |
| `MY_PACKAGE_REPLACED` | Alarms do not survive app replacement | FR-037 |
| `TIMEZONE_CHANGED` | Every anchor is invalidated; the boundary itself may change regime | FR-038 |
| `TIME_SET` | The backwards-clock case; the ledger prevents a second post | FR-041, SC-013 |

## `NotificationWorker` — `:data`

The only place the pieces meet. On each run it:

1. refreshes `BoundaryStatus` with the injected `TimeProvider`'s `now` and `zone`;
2. loads plan, completions, streak, preferences and ledger;
3. calls `evaluateAnchor` for the anchor that triggered it, if any;
4. posts, withdraws-and-records, or writes a `HELD` row per the verdict;
5. derives `weekClosesAt` through `weekCloseInstant(boundary)` and nowhere else, calls
   `buildNotificationPlan`, hands the plan's `anchors` to `replaceAll`, and schedules its `refreshAt`
   whether or not there are any anchors;
6. prunes ledger rows older than ninety days.

Steps 3 and 5 both run on every wake, including a bare refresh with no triggering anchor. That is what
makes the schedule self-healing after a reboot, a regime change, or a permission grant.

---

## `NotificationReconciler` — `:app`

Not a port; the in-process collector that satisfies FR-011 and FR-020 (research R5). Collects the
current date's completions in the application scope and withdraws:

- a posted prayer nudge whose section has reached its occurrence limits;
- the posted streak reminder once the day is counted.

It lives in `:app` and touches no repository write path, so `CompletionRepository` never learns that
notifications exist (Principle II).

---

## Testing obligations

| What | Where | Note |
|---|---|---|
| `MIGRATION_5_6` leaves `day_plans` and `completions` byte-identical | `:data` androidTest | The Principle III merge gate |
| Ledger idempotency across simulated reboot and backwards clock | `:data` androidTest | SC-013 |
| Exact vs relaxed mode selection and disclosure | `:data` androidTest | FR-036b, SC-007a |
| Deep-link seeds the `Destination` stack once, not on recomposition | `:app` androidTest | research R7 |
| Every string this feature adds, against the Principle IX list | `:app` review + fixture | SC-009, gating |

**By hand, on a device, recorded rather than pretended** (see `quickstart.md`): that a notification
appears in the tray at the right minute, and that refusing exact-alarm permission degrades as FR-036b
says. Both need a wall clock and a physical device; spec 007 set the precedent of recording such items
explicitly rather than claiming automated coverage.
