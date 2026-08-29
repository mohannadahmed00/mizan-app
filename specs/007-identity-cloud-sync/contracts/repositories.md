# Contract: new `:domain` repository interfaces

Four new interfaces, declared in `:domain` and implemented in `:data`, exactly like every existing
repository. **No existing interface is modified** — `CompletionRepository`, `DayPlanRepository` and
`CatalogueRepository` keep the signatures `002` gave them, and sync reaches them by decoration
(research R2). That is the whole point of Principle V, collected in this increment.

Nothing below names a Supabase, Ktor, WorkManager, Room, or DTO type. `ModuleBoundaryTest` keeps
`:domain`'s classpath free of all of them.

---

## `AccountRepository`

```kotlin
interface AccountRepository {

    /** The current session. Emits immediately and on every change, including silent renewal. */
    fun observeSession(): Flow<AccountSession>

    /**
     * Asks the account service to send a one-time code to [email].
     *
     * Never stores, sets, or transmits a password — there is none in this product (FR-002).
     * Offline returns [CodeRequest.NeedsConnection]; every local record is untouched either way.
     */
    suspend fun requestCode(email: String): CodeRequest

    /**
     * Completes sign-in. On success the session becomes SignedIn and the caller is
     * responsible for triggering migration ([SyncRepository.syncNow]).
     *
     * An expired or incorrect code is an ordinary outcome, not an exception, and never
     * discards the entered address or any local record (FR-002a).
     *
     * [replaceLocalRecords] MUST be left false on the first call. When the device holds a
     * different account's records this returns [CodeConfirmation.WouldReplaceLocalRecords]
     * and opens no session; the caller obtains the confirmation FR-013a requires and calls
     * again with true. There is no other way to remove a prior account's records.
     */
    suspend fun confirmCode(
        email: String,
        code: String,
        replaceLocalRecords: Boolean = false,
    ): CodeConfirmation

    /**
     * Ends the session.
     *
     * [SignOutMode.KEEP_LOCAL_RECORDS] leaves every record on the device, fully usable
     * signed-out (FR-007a). [SignOutMode.REMOVE_LOCAL_RECORDS] additionally clears them
     * (FR-007b). Neither removes anything from the account (FR-007d).
     *
     * Callers MUST have surfaced [SyncRepository.pendingCount] first when it is non-zero
     * (FR-007c); this method does not prompt.
     */
    suspend fun signOut(mode: SignOutMode)

    /** Optional, empty by default, editable at any time, never required (FR-007e). */
    suspend fun updateDisplayName(name: String?)
}

sealed interface CodeRequest {
    data class Sent(val resendAvailableAt: Instant) : CodeRequest
    data object NeedsConnection : CodeRequest
    data class AddressNotAccepted(val reason: String) : CodeRequest
}

sealed interface CodeConfirmation {
    data class SignedIn(val session: AccountSession.SignedIn) : CodeConfirmation
    data class NotAccepted(val reason: CodeRejection) : CodeConfirmation
    data object NeedsConnection : CodeConfirmation

    /**
     * A different account is signing into a device that holds this one's records.
     * No session was opened and nothing was changed. The caller MUST obtain the
     * confirmation FR-013a requires — naming [currentEmail], [recordedDays],
     * [completionCount] and [unsyncedCount] — before calling
     * [AccountRepository.confirmCode] again with `replaceLocalRecords = true` (R8).
     */
    data class WouldReplaceLocalRecords(
        val currentEmail: String,
        val recordedDays: Int,
        val completionCount: Int,
        val unsyncedCount: Int,
    ) : CodeConfirmation
}
```

**Guarantees**

1. No method throws for an ordinary outcome — offline, expired code, wrong code and foreign-account
   sign-in are all values.
2. No method touches a recorded day, a completion, or a score. The only one that removes a local
   record is `signOut(REMOVE_LOCAL_RECORDS)`, and only after the caller's confirmation.
3. `observeSession()` survives process death: a renewed session emits without user action (FR-005),
   and a session that cannot be renewed emits `SignedOut` with every local record intact (FR-006).

---

## `SyncRepository`

```kotlin
interface SyncRepository {

    fun observeStatus(): Flow<SyncStatus>

    /** Changes not yet accepted by the account. Drives FR-007c's sign-out warning. */
    fun observePendingCount(): Flow<Int>

    /**
     * Requests a sync now. Returns immediately — never blocks an interaction (FR-014).
     * Idempotent: calling it during a run does not start a second one.
     */
    fun syncNow()
}
```

**Guarantees**

1. `syncNow()` is a request, not a transfer. Nothing on the recording path awaits it.
2. Status is derived, never stored: `deriveSyncStatus(session, pending, reachable, coverage)` is a
   pure function in `:domain`, so the same inputs produce the same status everywhere (Principle VII's
   "no second opinion", applied to status rather than to time).
3. Failure is never a status. `NotSyncing` is the strongest thing this interface can say, and it says
   nothing about the user (Principle IX).

---

## `RecordCoverageRepository`

```kotlin
interface RecordCoverageRepository {
    /** How far back this device knows the record. Signed out: always complete (R9). */
    fun observeCoverage(): Flow<RecordCoverage>

    suspend fun coverage(): RecordCoverage
}
```

**Guarantees**

1. Signed out, or signed in with backfill finished, this returns
   `RecordCoverage.completeFrom(earliestPlanDate())` — the exact floor `003` already uses, so the
   offline product's behaviour is unchanged.
2. `complete = false` never means "empty". A date below `knownFrom` is `NOT_YET_KNOWN`, never 0%,
   never untouched, never absent (FR-023b).
3. The floor only moves backwards within a session; it never advances forwards as a side effect of a
   read.

---

## `CataloguePublicationRepository`

```kotlin
interface CataloguePublicationRepository {
    /**
     * Pulls published catalogue versions and inserts any this app understands and does not
     * already hold. Never alters, replaces, or removes a version already present (R10).
     *
     * A publication whose format this app cannot read is skipped in favour of the newest
     * one it can (FR-028) — never a crash, never a partial write.
     */
    suspend fun pullIfNewer(): PullOutcome
}

sealed interface PullOutcome {
    data class Added(val versions: List<Int>) : PullOutcome
    data object NothingNew : PullOutcome
    data class Skipped(val unreadableVersions: List<Int>) : PullOutcome
    data object Unreachable : PullOutcome
    data class Rejected(val defects: List<CatalogueDefect>) : PullOutcome
}
```

**Guarantees**

1. Every payload passes the same gate as the local seed — `scanForAuthoringAffordances`,
   `parseCatalogue` (with `ignoreUnknownKeys = false`), then `CatalogueValidator` — before a row is
   written. A payload carrying `editable`, `custom`, `userId` or any other authoring field is
   rejected wholesale (FR-027, Principle VI).
2. `Unreachable` and `Rejected` both leave the built-in seed in place and the app fully usable
   (FR-025).
3. Insert-only. There is no method here, and no DAO method beneath it, that can change a version
   already stored — which is what makes FR-026 and SC-009 structural rather than aspirational.

---

## What is *not* here, and why

| Not added | Because |
|---|---|
| A `userId` parameter on any existing read | The device carries one account's records at a time (R8), so no read needs scoping. |
| A `sync()` method on `CompletionRepository` | Sync is a decorator's concern; the interface stays the local-record contract `002` wrote. |
| A `DayPlanRepository.update…` method | Day plans are still written once. The only write paths added anywhere are DAO-level sync bookkeeping (`claimPlansForUser`, `claimPlannedTasksForUser`, `markSynced`), they live in `:data`, they are unreachable from `:domain`, and **none of them can touch a date, a catalogue version, a task set, or a points value** (FR-024a). |
| A remote-first read anywhere | Principle IV. The pull writes into Room; the UI observes Room. |
