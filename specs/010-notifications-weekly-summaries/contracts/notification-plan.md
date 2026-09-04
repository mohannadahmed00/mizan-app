# Contract: Notification Plan and Anchor Evaluation

**Feature**: `specs/010-notifications-weekly-summaries`

The two pure functions the whole feature turns on. Both live in `:domain`, take every input as a
parameter, read no clock, no location and no storage, and are what makes this increment testable with
literals.

---

## `buildNotificationPlan` — what to schedule

```kotlin
fun buildNotificationPlan(
    now: Instant,
    zone: ZoneId,
    boundary: BoundaryState,
    prayerTimes: PrayerTimes?,     // null on the Fallback regime — FR-016
    plan: DayPlan?,
    completions: List<Completion>,
    streak: StreakSummary,
    preferences: NotificationPreferences,
    weekClosesAt: Instant?,        // null when not yet knowable — see "Who computes weekClosesAt"
    ledger: List<DeliveryRecord>,
): NotificationPlan

data class NotificationPlan(
    /** Anchors still worth scheduling, ordered by `firesAt`. May be empty. */
    val anchors: List<NotificationAnchor>,
    /** When to wake and rebuild, regardless of whether anything is scheduled. Never null. */
    val refreshAt: Instant,
)
```

`anchors` holds every anchor still worth scheduling, ordered by `firesAt`. Anchors already terminal in
`ledger`, already passed, or belonging to a disabled category are simply absent from the list —
absence is the whole mechanism, so there is no "scheduled but suppressed" state to get wrong.

### Why `refreshAt` is a separate field and not a fourth category

The app must wake at each day's end even when every category is silenced — otherwise nothing rebuilds
the schedule on a phone that is never opened, and a week close is missed entirely (FR-023, FR-037).

That wake carries no notification, so it cannot be a `NotificationAnchor`: `NotificationAnchor.category`
is a `NotificationCategory`, which has exactly three values and every one of them names something the
person sees. Adding a fourth would put a non-notification into the type the presenter, the ledger and
the copy review all iterate over, and FR-001 fixes that enum at three.

`refreshAt` is therefore a plain `Instant` beside the list. It is always populated — normally
`boundary.expiresAt` — and `allSilenced` does not suppress it.

### Rules it encodes

| Rule | Source |
|---|---|
| `preferences.allSilenced` ⇒ `anchors` empty, always — but `refreshAt` still populated | FR-002, FR-005 |
| `refreshAt` is always populated, whatever the preferences say | FR-023, FR-037 |
| A category not in `preferences.enabled` contributes no anchor | FR-002 |
| `prayerTimes == null` ⇒ no `PRAYER_WINDOW` anchor at all, ever | FR-016 |
| Exactly five prayer anchors maximum, one per mapped section | FR-008, FR-014 |
| Each prayer anchor fires at `prayerInstant + NudgeWindow.OFFSET_AFTER_PRAYER` and carries the next prayer instant as `windowEndsAt` | FR-009, FR-012 |
| A section already at its occurrence limits contributes no anchor | FR-010 |
| `STREAK_AT_RISK` anchored at `StreakClock.nextBoundaryAfter(now, boundary.expiresAt)`, and only when `streak.current >= 1 && !streak.todayCounted` | FR-017, FR-018, FR-019, FR-022 |
| `WEEKLY_SUMMARY` anchored at `weekClosesAt`, omitted when it is null or when the summary is dormant | FR-023, FR-030a |
| An anchor whose `anchorKey` is terminal in `ledger` is omitted | FR-041 |
| Nothing is backfilled: an anchor whose `firesAt` is at or before `now` is omitted | FR-016, FR-030b, User Story 4 §5 |

**`prayerTimes` is nullable rather than an outcome type.** The caller has already resolved the
regime; passing `Calculated`/`NoLocation`/`CalculationFailed` down here would give the fallback
decision a second home, which Phase 9's own contract forbids. Null means "the fallback regime is in
force", and this function's only response to it is to schedule no nudges.

**`weekClosesAt` is a parameter, not a computation.** It comes from the single existing week rule.
This function may not derive it, or Principle VII has two opinions about when a week ends.

### Who computes `weekClosesAt`, and how

The caller does, through one named function so there is exactly one implementation:

```kotlin
// domain/notification/WeekCloseInstant.kt
fun weekCloseInstant(boundary: BoundaryState): Instant?
```

Rule, and the whole of it:

1. `val week = WeekBoundary.weekContaining(boundary.resolvedDate)` — the existing week rule, reused.
2. If `boundary.resolvedDate == week.end`, the week closes when this accountability day ends:
   return `boundary.expiresAt`.
3. Otherwise the close is not yet knowable from resolved state alone — return `null`, and no summary
   anchor is scheduled on this pass.

Returning null rather than projecting forward is deliberate. The close instant is Friday's Maghrib,
which depends on a calculation for a future date; projecting it would mean computing a boundary
instant outside the boundary provider, which Principle VII forbids. The refresh anchor guarantees the
app wakes at every day's end, so the summary anchor is always scheduled on the Friday itself, at the
latest a full accountability day before it fires.

---

## `evaluateAnchor` — post or discard, at fire time

```kotlin
fun evaluateAnchor(
    anchor: NotificationAnchor,
    now: Instant,
    zone: ZoneId,
    boundary: BoundaryState,
    plan: DayPlan?,
    completions: List<Completion>,
    streak: StreakSummary,
    preferences: NotificationPreferences,
    summary: WeekSummary?,          // populated only for WEEKLY_SUMMARY
    ledger: DeliveryRecord?,        // this anchor's row, if any
    hasPermission: Boolean,
): NotificationVerdict
```

Evaluated fresh at the moment the alarm fires — never at schedule time — because everything it checks
can have changed since. Order matters, and the tests assert the order, because it determines which
`DiscardReason` is reported:

1. `ledger` terminal → `Discard(ALREADY_DELIVERED)`
2. `!hasPermission` → `Discard(NO_PERMISSION)`
3. `preferences.allSilenced` → `Discard(ALL_SILENCED)`
4. category disabled → `Discard(CATEGORY_OFF)`
5. `anchor.speaksFor` names a date or week other than the one now in force → `Discard(DAY_ROLLED_OVER)`
6. prayer anchor and `now >= windowEndsAt` → `Discard(WINDOW_PASSED)`
7. prayer anchor and the section is complete → `Discard(SECTION_COMPLETE)`
8. streak anchor and `streak.todayCounted` → `Discard(DAY_ALREADY_COUNTED)`
9. streak anchor and `streak.current == 0` → `Discard(NO_LIVE_STREAK)`
10. summary anchor and dormant → `Discard(SUMMARY_DORMANT)`
11. quiet hours contain `now`:
    - `WEEKLY_SUMMARY` → `Hold(quietHours.endAfter(now, zone))`
    - otherwise → `Discard(QUIET_HOURS)`
12. otherwise → `Post(content)`

**Permission is checked before preferences on purpose.** A person who never granted permission should
see `NO_PERMISSION` in the ledger, not `CATEGORY_OFF` — the settings surface's statement (FR-007)
depends on telling those two apart.

**Step 6 uses `>=`, not `>`.** An anchor firing exactly at the next prayer instant belongs to the next
window, and two nudges must never arrive together.

**Step 11 is the only place `Hold` is produced**, and only for one category. Everything else that
meets quiet hours dies there (FR-033).

---

## `NotificationContent`

```kotlin
data class NotificationContent(
    val category: NotificationCategory,
    val titleKey: String,
    val bodyArgs: Map<String, String>,
    val destination: String,      // the MainActivity Destination codec — research R7
)
```

**No rendered string crosses the `:domain` boundary.** `:domain` decides *what* to say and *what it is
about*; `:app` owns the words, because the words are interface text in the English shell and Arabic
section content has to be rendered with the app's bidirectional discipline.

### Copy constraints, enforced by review not by type (SC-009)

| Category | Must convey | Must never |
|---|---|---|
| `PRAYER_WINDOW` | the section, and what is available in it | a count of what was missed; "you forgot"; any deficit framing |
| `STREAK_AT_RISK` | that one task continues an established streak | loss, breakage, expiry, countdown, or a consequence of inaction |
| `WEEKLY_SUMMARY` | days engaged, tasks recorded, points earned | a count of anything not done; any comparison to another person; any figure framed as falling short |

---

## Ports the plan is executed through

Declared in `:domain`, implemented in `:data`. They exist because the tests substitute them now — not
speculatively.

```kotlin
interface NotificationScheduler {
    suspend fun replaceAll(anchors: List<NotificationAnchor>)
    suspend fun cancelAll()
    fun deliveryMode(): DeliveryMode      // EXACT or RELAXED — FR-036a, FR-036b
}

interface NotificationPresenter {
    suspend fun post(anchor: NotificationAnchor, content: NotificationContent)
    suspend fun withdraw(anchorKey: String)
    fun hasPermission(): Boolean
}
```

`replaceAll` is deliberately wholesale: research R8 establishes that the schedule is disposable and
the ledger is the memory, so rebuilding everything is idempotent and there is no incremental path to
get wrong. `cancelAll` is what FR-005 calls at the instant a switch flips.
