# Contract: the sync engine

`data/sync/`. Four responsibilities — enqueue, drain, pull, backfill — plus the one destructive path
in the product, which only a user confirmation can reach.

The engine has exactly one hard rule above all others: **nothing it does may sit on the path of
viewing tasks, recording a completion, undoing one, or computing a score** (Principle IV, FR-014).

---

## 1. Enqueue — `Outbox`

```kotlin
/**
 * One queued change, as the engine sees it. Declared in the same file as [Outbox].
 * The Room row is `OutboxEntity`; this is its in-memory form, with the id derived
 * rather than supplied so a caller cannot get it wrong.
 */
data class OutboxEntry(
    val entityType: EntityType,
    val entityId: String,           // completion UUID, ISO date, or user id
    val operation: Operation,
    val payload: String,            // JSON body as it will be sent
    val attempts: Int = 0,
) {
    val id: String get() = "${entityType.wire}:$entityId:${operation.wire}"

    enum class EntityType(val wire: String) { COMPLETION("completion"), DAY_RECORD("day_record"), PROFILE("profile") }

    /** There is no DELETE: an undo is a tombstone carried by an UPSERT (FR-018). */
    enum class Operation(val wire: String) { UPSERT("UPSERT") }
}

class Outbox(private val db: MizanDatabase, private val time: TimeProvider) {

    /** Called inside the caller's transaction. Deterministic id, so this is idempotent (R6). */
    suspend fun enqueue(entry: OutboxEntry)

    suspend fun due(now: Instant, limit: Int): List<OutboxEntry>
    suspend fun accepted(ids: List<String>)          // the only path that removes an entry
    suspend fun deferred(ids: List<String>, at: Instant)
    fun observePendingCount(): Flow<Int>
}
```

`id = "$entityType:$entityId:$operation"`. Re-enqueueing replaces the payload and leaves one row.

**Where enqueue happens**: `SyncingCompletionRepository.record` / `.undoLast` and
`SyncingDayPlanRepository.ensurePlanFor`, each delegating to the Room implementation and enqueueing
in the same Room transaction. Either both land or neither does — a completion that exists locally
with no queue entry is a record that silently never reaches the account, and FR-015 exists to
prevent it.

**What is enqueued**

| Local event | Entry |
|---|---|
| `record(date, slug)` | `completion:<uuid>:UPSERT` **and** `day_record:<date>:UPSERT` |
| `undoLast(date, slug)` | `completion:<uuid>:UPSERT` with `reversedAt` set — a tombstone, never a delete (FR-018) |
| `ensurePlanFor(date)` creates a plan | `day_record:<date>:UPSERT` |
| display name changed | `profile:<userId>:UPSERT` |

Enqueueing while signed out is a no-op: there is no account to send to, and first sign-in claims and
enqueues everything anyway (§5).

---

## 2. Drain

```text
for each batch of `due(now, limit = 200)`, oldest first:
    group by entityType
    upsert via RemoteDataSource
    Ok         -> accepted(ids); mark syncedAt on the local rows
    Unreachable-> deferred(ids, RetrySchedule.nextAttemptAt(attempt, now)); stop this run
    Rejected   -> deferred(the named ids); continue with the rest of the batch
    NotAuthenticated -> stop, sign out locally, keep every record (FR-006)
```

- Oldest-first, so a long offline period drains in the order the user lived it.
- `syncedAt` is set **only** after acceptance. Until then the row is `Pending` and the app does not
  claim it is backed up (FR-012).
- An entry is never dropped, expired, or evicted, whatever `attempt` reaches (FR-021a).
- A `Rejected` entry stays queued and keeps retrying on a slower schedule; the local record is never
  touched (spec Edge Case: "reachable but rejects a write").

---

## 3. Pull (incremental)

```text
cursor = sync_cursors["pull_cursor"]
changes = changedSince(cursor, limit = 500)
in one transaction:
    for each remote day record:  merge into day_plans      (§6)
    for each remote completion:  merge into completions    (§6)
    cursor = changes.watermark
```

Runs after every drain and on every foregrounding, so a device that has just uploaded also learns
what the other device did. Only rows at or after `backfill_floor` are applied; anything older is the
backfill's job and would otherwise create a hole above the floor.

---

## 4. Backfill (first sign-in on a device, and any resumption)

```text
head:      recordsBetween(currentWeekStart, today)  -> app is usable (SC-006, FR-023a)
then loop: to   = backfill_floor - 1 day
           from = to - 89 days
           page = recordsBetween(from, to)
           apply page in one transaction
           backfill_floor = from
           until from <= earliestRecordedDate()  -> backfill_complete = true
```

- The floor is committed with each page, so an interruption costs at most one page, and re-applying
  a page is harmless because every write is an upsert (FR-023c).
- While `backfill_complete` is false, `RecordCoverage(knownFrom = backfill_floor, complete = false)`
  is what the read models see. Dates below the floor render `NOT_YET_KNOWN` (FR-023b) and
  `GetWeekSummary` does **not** materialise plans for them — that would create a competing day
  record for a date the account already holds.
- Streak, weekly and insight figures covering an incomplete range are marked provisional (FR-023d).

---

## 5. First sign-in: claim, enqueue, drain, pull

```text
1. account_scope := (userId, email, displayName)
2. UPDATE day_plans     SET userId = :u WHERE userId IS NULL
   UPDATE planned_tasks SET userId = :u WHERE userId IS NULL
   UPDATE completions   SET userId = :u WHERE userId IS NULL
3. enqueue one entry per claimed day record and per claimed completion, oldest date first
4. drain (§2), then pull (§3) and backfill (§4)
```

Every step is idempotent and the sequence is resumable at any point (research R7). **No local row is
deleted, overwritten, or recomputed at any point, including on failure** (FR-010). The union in
FR-011 falls out of upsert semantics plus the pull — records only one side has are simply added.

**A different account signing in** (`account_scope.userId` is set and differs) does not reach step 1.
`confirmCode(email, code, replaceLocalRecords = false)` returns `WouldReplaceLocalRecords`, carrying
the account being replaced, the local record count and the unsynced count. Only a second call with
`replaceLocalRecords = true` — made after the confirmation FR-013a requires, which names the account
and what is about to be removed — runs `LocalRecordWipe` and then begins the sequence above
(research R8, FR-013, FR-013a). Declining changes nothing and opens no session.

---

## 6. Ingest merges

**Day record.** Exactly two outcomes, and one of them is "do nothing":

- **A local plan already exists for that date → nothing happens.** Not a re-derivation, not a version
  change, not a recalculated total. The incoming record is discarded for ingest purposes (the
  device's own version is still pushed, and the server settles on the lower of the two — FR-024b).
  This is FR-024a, and it is the whole of the rule.
- **No local plan for that date** → take `mergeDayRecord(local = null, remote).catalogueVersion` and
  build the plan with `buildDayPlan(catalogueAt(version), version, date, origin = BACKFILLED)`.
  If that catalogue version is not held locally, leave the date `NOT_YET_KNOWN` and request a
  catalogue pull — never guess a version, never drop the record.

`mergeDayRecord`'s `min` therefore governs what the **account** stores and what a device sees when it
first materialises a date. It can never reach a day that is already recorded, which is why no
`adoptMergedVersion` exists (research R5).

**Completion.** `mergeCompletion(local, remote)`: insert if absent; otherwise apply the tombstone if
either side has one. `pointsAwarded`, `recordedAt`, `creditedDate` and `taskSlug` are never rewritten
by an ingest — they are the frozen record (Principle III). `dayPlanId` is bound locally from
`creditedDate`.

---

## 7. Scheduling

| Trigger | Work |
|---|---|
| A change is enqueued | unique expedited `SyncWorker`, `NetworkType.CONNECTED` |
| App foregrounded | same, plus a pull |
| Connectivity returns | WorkManager's own constraint releases the pending work — this is what makes SC-003's one minute real, with the app closed |
| Backfill incomplete | the worker chains the next page after each committed one |

No fixed polling interval is promised to the user (spec Assumptions). WorkManager's exponential
backoff governs the worker; `RetrySchedule` governs which entries are due inside a run.

---

## 8. Status

`deriveSyncStatus(session, pendingCount, reachable, coverage)` — pure, in `:domain`:

| Condition | Status |
|---|---|
| `session is SignedOut` | `NotSignedIn` |
| coverage incomplete | `LoadingEarlierDays(knownFrom)` |
| `pendingCount > 0` and reachable | `Pending(count)` |
| `pendingCount > 0` and not reachable | `NotSyncing` |
| otherwise | `UpToDate` |

Backfill outranks pending because a user whose history is still arriving needs to know that before
anything else (FR-023b). No branch produces a failure, a blame, or a red state (Principle IX,
SC-011).

---

## 9. `LocalRecordWipe`

The only code in the product that deletes a user record. Reachable from exactly two places, both
requiring an explicit confirmation that names what will be removed:

- `signOut(REMOVE_LOCAL_RECORDS)` (FR-007b)
- a different account signing in (research R8, FR-013)

It clears `day_plans`, `planned_tasks`, `completions`, `outbox`, `sync_cursors` and `account_scope`
in one transaction. It **never** touches the catalogue tables — the seed and any pulled versions stay,
so the app is immediately usable afterwards — and it never sends anything to the account, which
keeps everything it had already accepted (FR-007d).
