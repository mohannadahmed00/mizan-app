# Phase 1 Data Model: Identity & Cloud Sync

Three layers: what `:domain` knows, what Room stores, and what Postgres stores. The rule that keeps
them apart is unchanged — `:domain` names concepts, `:data` owns both persistence shapes, and no
Supabase or Ktor type crosses out of `data/sync/`.

## 1. Domain concepts (`:domain`, pure Kotlin)

### `AccountSession`

```kotlin
sealed interface AccountSession {
    data object SignedOut : AccountSession
    data class SignedIn(
        val userId: String,
        val email: String,
        val displayName: String?,   // optional, empty by default (FR-007e)
    ) : AccountSession
}
```

`displayName` is null until the user sets one and may be cleared back to null. Nothing in this
increment publishes it to another user; where a name is shown and none is set, the email is shown
instead (FR-007e).

### `SignInStep`

The delivery round-trip, as required by FR-002a. The entered email survives every transition:

```kotlin
sealed interface SignInStep {
    data class EnteringEmail(val email: String, val invalid: Boolean = false) : SignInStep
    data class RequestingCode(val email: String) : SignInStep
    data class AwaitingCode(val email: String, val resendAvailableAt: Instant) : SignInStep
    data class Confirming(val email: String, val code: String) : SignInStep
    data class CodeNotAccepted(val email: String, val reason: CodeRejection) : SignInStep
    data class NeedsConnection(val email: String) : SignInStep
}

enum class CodeRejection { EXPIRED, INCORRECT }
```

`NeedsConnection` is a first-class step, not an error: US1 AS3 requires an offline sign-in attempt to
state that sign-in needs a connection, leave every local record untouched, and leave the app usable.

### `SignOutMode`

```kotlin
enum class SignOutMode { KEEP_LOCAL_RECORDS, REMOVE_LOCAL_RECORDS }
```

`KEEP_LOCAL_RECORDS` is plain sign-out (FR-007a). `REMOVE_LOCAL_RECORDS` is the separately labelled
shared-device action (FR-007b) and is the only path in the product that deletes a local record.
Neither removes anything from the account (FR-007d).

### `SyncStatus`

```kotlin
sealed interface SyncStatus {
    data object NotSignedIn : SyncStatus
    data object UpToDate : SyncStatus
    data class Pending(val count: Int) : SyncStatus
    data object NotSyncing : SyncStatus                       // signed in, account unreachable
    data class LoadingEarlierDays(val knownFrom: LocalDate?) : SyncStatus
}
```

Four states plus the backfill state, covering FR-022's minimum. Every rendering of these is neutral:
no "failed", no "error", no red, no count styled as a warning (Principle IX, SC-011). `Pending` and
`NotSyncing` are facts about the queue, never about the user.

### `RecordCoverage`

```kotlin
data class RecordCoverage(val knownFrom: LocalDate?, val complete: Boolean) {
    fun isKnown(date: LocalDate): Boolean =
        complete || knownFrom == null || !date.isBefore(knownFrom)

    companion object {
        fun completeFrom(recordStart: LocalDate?) = RecordCoverage(recordStart, complete = true)
    }
}
```

How far back the account's history is known **on this device**. Signed out this is always
`completeFrom(earliestPlanDate())`, which is exactly the record-start floor `003` already threads
through `buildWeekSummary` — so the signed-out product's behaviour is unchanged (R9).

### `DayCellState` — one new value

```kotlin
enum class DayCellState {
    OUTSIDE_RECORD,
    NOT_YET_ELAPSED,
    NOT_YET_KNOWN,      // NEW — in the record, but this device has not fetched it yet (FR-023b)
    NOTHING_RECORDED,
    PARTLY_RECORDED,
    FULLY_RECORDED,
}
```

`NOT_YET_KNOWN` is neutral like every other value: it is not a miss, and it must be visually distinct
from `NOTHING_RECORDED` — conflating them is precisely what FR-023b forbids. Adding an enum value
rather than a flag makes every `when`, including `ui/DayCellColors.kt`, a compile error until it is
handled.

### Merge rules (pure functions, `:domain`)

```kotlin
fun mergeDayRecord(local: DayRecord?, remote: DayRecord?): DayRecord
fun mergeCompletion(local: Completion?, remote: Completion?): Completion
```

| | Rule | Properties |
|---|---|---|
| Day record | `catalogueVersion = min(local, remote)` | commutative, associative, idempotent; can only settle a date on the **older** catalogue. **Read only by a device with no local plan for that date** — a device that already materialised it ignores the settled value entirely (FR-024a) |
| Completion | `reversedAt = local.reversedAt ?: remote.reversedAt`; every other field is write-once | commutative, associative, idempotent; once reversed, always reversed |

Neither reads a clock, and neither can change `pointsAwarded` (R5). A re-record after an undo is a
new row with a new UUID, so "the later action" in FR-019 is always the reversal — last-write-wins and
monotone merging give the same answer, and monotone gives it without trusting a device clock.

### `DayRecord`

The syncable projection of a day — the version pointer, not the plan:

```kotlin
data class DayRecord(val date: LocalDate, val catalogueVersion: Int)
```

`DayPlan` itself is unchanged and stays local: `plannedTasks` and `availablePoints` are derived
from `(catalogueAt(catalogueVersion), catalogueVersion, date)` by the existing `buildDayPlan` (R4)
— **once, when the date is first materialised on that device, and never again** (FR-024a). An
incoming `DayRecord` for a date that already has a local plan changes nothing.

### `RetrySchedule`

```kotlin
object RetrySchedule {
    fun nextAttemptAt(attempt: Int, from: Instant): Instant   // exponential, capped, never "give up"
}
```

There is deliberately no expiry, no maximum attempt count, and no `shouldDrop`. FR-021a makes a
pending change the only copy of a fact the user believes is recorded, so the type cannot express
discarding one.

## 2. Local storage (Room, `:data`) — migration 2 → 3, additive only

### New: `outbox`

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | `"$entityType:$entityId:$operation"` — deterministic, so enqueue is idempotent (R6) |
| `entityType` | TEXT | `completion` \| `day_record` \| `profile` |
| `entityId` | TEXT | completion UUID, ISO date, or user id |
| `operation` | TEXT | `UPSERT` (there is no `DELETE` — undo is a tombstone upsert) |
| `payload` | TEXT | JSON body as it will be sent |
| `createdAt` | INTEGER | epoch millis, from `TimeProvider` |
| `attempts` | INTEGER | for `RetrySchedule` only |
| `nextAttemptAt` | INTEGER | epoch millis; due entries are those at or before now |

Index on `nextAttemptAt`. Entries are removed **only** on acceptance by the account. Nothing expires
one, caps the table, or evicts by age (FR-021a). Size: a heavy year is ~25 000 entries at ~200 bytes,
≈5 MB — the cost the spec's Edge Case asks to stay negligible (SC-010).

### New: `sync_cursors`

| Column | Type | Notes |
|---|---|---|
| `key` | TEXT PK | `pull_cursor`, `backfill_floor`, `backfill_complete` |
| `value` | TEXT | ISO timestamp, ISO date, or boolean |

A key-value table rather than typed columns, because each cursor is written independently and a
partially-written row must not be representable.

### New: `account_scope` (exactly one row)

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK | always `0`, `CHECK (id = 0)` |
| `userId` | TEXT NULL | null = the device has never held an account's records |
| `email` | TEXT NULL | shown in the profile |
| `displayName` | TEXT NULL | optional, empty by default |
| `updatedAt` | INTEGER | |

The device carries one account's records at a time. Signing a different account in requires the same
confirmed wipe as "sign out and remove data" (R8, FR-013). Plain sign-out leaves this row and every
record intact — the session ends, the scope does not (FR-007a).

### Changed: `day_plans`, `completions`

One nullable column each: `syncedAt INTEGER NULL`. Set when the account has accepted that row's
write; the source of "backed up" (FR-012) and, by absence, of `SyncStatus.Pending`. Nothing else on
either table changes — `id`, `updatedAt`, `deletedAt` and `userId` were already there from `002`
(Principle V), so **no data migration of user history is required**.

### New DAO methods, and their deliberate narrowness

| DAO | Method | What it can touch |
|---|---|---|
| `DayPlanDao` | `claimPlansForUser(userId, at)` | `userId`, `updatedAt` on `day_plans` — `WHERE userId IS NULL` |
| `DayPlanDao` | `claimPlannedTasksForUser(userId, at)` | `userId`, `updatedAt` on `planned_tasks` — `WHERE userId IS NULL` |
| `DayPlanDao` | `markSynced(dates, at)` | `syncedAt` |
| `CompletionDao` | `claimForUser(userId, at)` | `userId`, `updatedAt` — `WHERE userId IS NULL` |
| `CompletionDao` | `markSynced(ids, at)` | `syncedAt` |
| `CompletionDao` | `upsertFromRemote(row)` | insert, or apply the tombstone merge — never `pointsAwarded` |
| `CatalogueDao` | *(no new write method)* | pulls insert absent versions only (R10) |

**Every one of these writes sync bookkeeping and nothing else.** No method above — and no method
anywhere in the increment — can change a stored day's date, catalogue version, task set, per-task
points, or available-points total. That is FR-024a expressed as an absence rather than a promise: an
interface that cannot express the forbidden operation is stronger than one that merely avoids
calling it, which is the rule `002` set for `DayPlanDao` and this increment keeps.

### Migration 2 → 3

```sql
CREATE TABLE outbox (…);
CREATE TABLE sync_cursors (…);
CREATE TABLE account_scope (…);
ALTER TABLE day_plans  ADD COLUMN syncedAt INTEGER;
ALTER TABLE completions ADD COLUMN syncedAt INTEGER;
CREATE INDEX index_outbox_nextAttemptAt ON outbox (nextAttemptAt);
```

Purely additive: no column dropped, renamed, or rewritten; no row touched. Schema exported to
`data/schemas/com.giraffe.mizanapp.data.db.MizanDatabase/3.json` and committed, and
`MizanDatabaseMigrationTest` asserts a v2 database keeps every plan, planned task and completion
with identical figures.

## 3. Remote storage (Postgres / Supabase)

Full DDL and policies in [contracts/remote-schema.sql](./contracts/remote-schema.sql). Shape only,
here:

| Table | Key | Holds | Written by |
|---|---|---|---|
| `profiles` | `id` = `auth.users.id` | optional display name | its owner |
| `day_records` | `(user_id, date)` | `catalogue_version` | its owner, upsert with `LEAST()` |
| `completions` | `id` (client UUID) | `credited_date`, `task_slug`, `points_awarded`, `recorded_at`, `reversed_at` | its owner, upsert with `COALESCE()` on the tombstone |
| `catalogue_publications` | `version` | `effective_from`, `format_version`, `payload` | nobody, through the API (R10) |

Notes that matter:

- **`completions` carries no `day_plan_id`.** The local plan id is a device-local UUID; the remote
  row is bound to a date, and ingest re-binds it to whatever plan that date has locally (R4).
- **`points_awarded` is transmitted and stored, never recomputed.** It is the frozen figure from the
  day the record was made, and no merge, pull, or publication is an input to it (Principle III).
- **`day_records.catalogue_version` settles server-side and is consumed by exactly one caller**: a
  device with no local plan for that date, materialising it for the first time. A device that
  already has a plan for the date never reads it and never changes anything (FR-024a, FR-024b).
- **There is no delete policy on any table.** Undo is `reversed_at`; removal from a device is a local
  operation that leaves the account untouched (FR-007d).
- **`updated_at` is server-assigned** (`default now()`, refreshed on upsert) and is used as a pull
  cursor and as ordering data only. It is never read as "now" and never decides a conflict —
  both merges are order-independent (Principle VII, R5).

## 4. State transitions

### Session

```text
SignedOut ──requestCode──> AwaitingCode ──confirm(ok)──> SignedIn
    ▲                            │                            │
    │                            └──confirm(expired/wrong)──> CodeNotAccepted ──> AwaitingCode
    │                                                          (email kept, records untouched)
    ├──signOut(KEEP)────────────────────────────────────────────┤   records stay, scope stays
    ├──signOut(REMOVE) [confirm names what is lost]─────────────┤   records cleared, scope cleared
    └──session unrenewable──────────────────────────────────────┘   records intact, app fully usable (FR-006)
```

### A completion

```text
recorded (local) ──enqueue──> pending ──accepted──> synced
     │                                                  │
     └──undo──> reversed (local) ──enqueue──> pending ──accepted──> synced (tombstone)
```

Reversal is terminal for that row. A re-record creates a new row with a new UUID; it never revives
this one. Every state above is visible offline and none of them blocks the interaction (FR-014).

### Coverage on a new device

```text
signed in ──head pull (today + current week)──> usable (SC-006: ≤10 s)
             │
             └──backfill page (90 days, descending)──> floor moves back ──> … ──> complete
                  dates below the floor render NOT_YET_KNOWN, never 0% (FR-023b)
                  streak / weekly / insight figures are provisional until complete (FR-023d)
```
