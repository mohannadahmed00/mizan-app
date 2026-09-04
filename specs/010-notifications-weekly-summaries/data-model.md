# Phase 1 Data Model: Notifications and Weekly Summaries

**Feature**: `specs/010-notifications-weekly-summaries` | **Date**: 2026-09-04

Two new tables, both device-local. **No table in this feature holds a computed figure** — FR-024a
forbids storing a week's numbers, and the weekly summary is derived on demand from records that
already exist.

---

## Domain types (`:domain`, no persistence)

### `NotificationCategory`

```kotlin
enum class NotificationCategory { PRAYER_WINDOW, STREAK_AT_RISK, WEEKLY_SUMMARY }
```

Fixed and closed (FR-001). Adding a fourth is a spec change, not a code change.

| Category | Default | Anchor source | Quiet hours |
|---|---|---|---|
| `PRAYER_WINDOW` | off (FR-003) | prayer instant + `NudgeWindow.OFFSET_AFTER_PRAYER` | dropped (FR-033) |
| `STREAK_AT_RISK` | off (FR-003) | `StreakClock.nextBoundaryAfter` over the day's `expiresAt` | dropped (FR-033) |
| `WEEKLY_SUMMARY` | **on** (FR-003) | the week's close instant | held, then delivered (FR-034) |

### `NotificationPreferences`

| Field | Type | Rule |
|---|---|---|
| `enabled` | `Set<NotificationCategory>` | Defaults to `{WEEKLY_SUMMARY}` (FR-003) |
| `allSilenced` | `Boolean` | The master control (FR-002). When true, nothing is scheduled regardless of `enabled` |
| `quietHours` | `QuietHours?` | Null means off, which is the default (FR-031) |

`allSilenced` is deliberately separate from clearing `enabled`: silencing must be reversible without
the person having to remember which categories they had chosen.

### `QuietHours`

| Field | Type | Rule |
|---|---|---|
| `start` | `LocalTime` | Device-local (FR-032) |
| `end` | `LocalTime` | May be earlier than `start`, meaning the window crosses midnight (FR-032) |

`contains(instant, zone)` is a pure predicate. `endAfter(instant, zone)` returns the instant the
window next closes, which is what a held summary is scheduled for (FR-034).

### `NotificationAnchor`

| Field | Type | Rule |
|---|---|---|
| `category` | `NotificationCategory` | |
| `firesAt` | `Instant` | |
| `speaksFor` | `AnchorSubject` | What the notification is *about* — never merely when it fires |

```kotlin
sealed interface AnchorSubject {
    data class PrayerWindow(val date: LocalDate, val sectionId: String, val windowEndsAt: Instant) : AnchorSubject
    data class Day(val date: LocalDate) : AnchorSubject
    data class ClosedWeek(val key: WeekKey) : AnchorSubject
}
```

`speaksFor` is the identity used by the delivery ledger and by FR-040's "never speak about another
date" rule. `windowEndsAt` on a prayer anchor is the *next* prayer instant, which is what makes the
staleness check of FR-012 a comparison rather than a recalculation.

### `NotificationVerdict`

```kotlin
sealed interface NotificationVerdict {
    data class Post(val content: NotificationContent) : NotificationVerdict
    data class Discard(val reason: DiscardReason) : NotificationVerdict
    data class Hold(val until: Instant) : NotificationVerdict
}

enum class DiscardReason {
    CATEGORY_OFF, ALL_SILENCED, ALREADY_DELIVERED, SECTION_COMPLETE, DAY_ALREADY_COUNTED,
    NO_LIVE_STREAK, WINDOW_PASSED, DAY_ROLLED_OVER, QUIET_HOURS, SUMMARY_DORMANT, NO_PERMISSION,
}
```

`DiscardReason` is exhaustive on purpose: every reason a notification is *not* shown is a named,
asserted case rather than a fallthrough, and the tests enumerate them.

---

## Room tables (`:data`, migration 5 → 6, additive)

### `notification_preferences` — single row

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER PK` | Always `0`, as `boundary_state` does |
| `prayerWindowEnabled` | `INTEGER NOT NULL DEFAULT 0` | Off by default (FR-003) |
| `streakAtRiskEnabled` | `INTEGER NOT NULL DEFAULT 0` | Off by default (FR-003) |
| `weeklySummaryEnabled` | `INTEGER NOT NULL DEFAULT 1` | **On** by default (FR-003) |
| `allSilenced` | `INTEGER NOT NULL DEFAULT 0` | |
| `quietStart` | `TEXT` | Nullable; `HH:mm`. Null with `quietEnd` means quiet hours off |
| `quietEnd` | `TEXT` | Nullable |
| `permissionAskedAt` | `INTEGER` | Nullable epoch millis. Enforces "asked once, not repeatedly" (FR-007) |

**Deliberately not synchronisable.** No `userId`, no `updatedAt`, no tombstone. Principle V governs
synchronisable rows; quiet hours are a property of *this* phone, and syncing them would mean a tablet
left on a desk deciding when a phone may speak. Same reasoning, same shape, as `boundary_state`.

### `notification_deliveries` — one row per anchor

| Column | Type | Notes |
|---|---|---|
| `anchorKey` | `TEXT PK` | Derived from `category` + `speaksFor`; e.g. `PRAYER:2026-09-04:asr`, `STREAK:2026-09-04`, `WEEK:2026-08-29` |
| `category` | `TEXT NOT NULL` | |
| `state` | `TEXT NOT NULL` | `DELIVERED`, `DISCARDED`, or `HELD` |
| `reason` | `TEXT` | The `DiscardReason` name when `DISCARDED`; null otherwise |
| `decidedAt` | `INTEGER NOT NULL` | Epoch millis, for pruning only — never a trust input |
| `heldUntil` | `INTEGER` | Set only in `HELD`; the quiet-hours end instant (FR-034) |

**The primary key is the idempotency guarantee.** FR-041 — at most one delivery per anchor across
reboots, backwards clock movement and repeated schedule re-derivation — is enforced by the key, not
by application logic that could be got wrong.

**Deliberately not synchronisable**, for a sharper reason than preferences: a delivery record from one
device must never suppress a notification on another. Two devices are two notification surfaces.

**Retention**: rows older than ninety days are pruned on each schedule refresh. The table is
disposable — FR-045 requires it to be reconstructible or discardable without affecting any figure the
app reports, and discarding it can at worst cause one repeat notification, never a lost or altered
record.

### Migration

`MIGRATION_5_6` creates both tables and does nothing else. It touches no history table. Its test
seeds a `day_plans` row and a `completions` row at version 5, runs the migration, and asserts every
column of both is unchanged — the Principle III test the merge gate requires of any increment
touching persistence.

---

## What is *not* stored

| Not stored | Why |
|---|---|
| Weekly summary figures | FR-024a. Derived on demand from recorded plans and completions, so the summary and the weekly sheet cannot disagree |
| Scheduled alarms | Rebuilt from `buildNotificationPlan` on every refresh (research R8). Disposable state is state that cannot drift |
| Streak values | Phase 4 forbids a streak table, and this feature reads `GetStreakSummary` like every other consumer |
| Prayer instants | Recomputed from the provider. Caching them would be a second source (Principle VII) |
| The dormancy counter | A fold over the closed weeks' recorded activity. Storing a counter would be a second opinion about what "empty" means |

---

## State transitions

**Delivery row**

```text
(absent) ──schedule──▶ (absent, alarm pending)
        ──fire, verdict Post────▶ DELIVERED   (terminal)
        ──fire, verdict Discard─▶ DISCARDED   (terminal)
        ──fire, verdict Hold────▶ HELD ──quiet hours end, Post──▶ DELIVERED
                                       ──quiet hours end, Discard─▶ DISCARDED
```

`DELIVERED` and `DISCARDED` are terminal: a second fire for the same `anchorKey` reads the row and
discards with `ALREADY_DELIVERED`. `HELD` is the only re-enterable state, and it re-enters at most
once because the second decision is terminal either way.

**Summary dormancy** — not persisted; recomputed as a fold over closed weeks.

```text
ACTIVE ──closed week with no recorded activity──▶ ACTIVE (1 empty)
       ──second consecutive empty week─────────▶ ACTIVE (2 empty, still announced)
       ──third consecutive empty week──────────▶ DORMANT (not announced, FR-030a)
DORMANT ──any completion recorded─────────────▶ ACTIVE at the next week close (FR-030b)
```
