# Contract: `RemoteDataSource`

The single seam between the sync engine and Supabase. Declared in
`data/src/main/kotlin/com/giraffe/mizanapp/data/sync/RemoteDataSource.kt`; implemented three times —
`SupabaseRemoteDataSource` (production), `NoOpRemoteDataSource` (bound when the build carries no
Supabase configuration: every method returns `RemoteResult.Unreachable`, so the app is the offline
MVP and nothing crashes or needs a null check), and `FakeRemoteDataSource` (every test in this
increment except the RLS SQL and one thin live contract test — research R14).

**The Koin binding is never nullable.** `SupabaseClientFactory` returning null selects
`NoOpRemoteDataSource`; callers always `get()` a real instance.

**No Supabase or Ktor type appears in this interface.** They exist only inside
`SupabaseRemoteDataSource` and `SupabaseClientFactory`, which are the only two files in the
repository that import them.

```kotlin
interface RemoteDataSource {

    // ---- writes: every one is an upsert, every one is idempotent (FR-017) ----

    /** Upsert on (user_id, date), merged server-side by LEAST(catalogue_version). */
    suspend fun upsertDayRecords(rows: List<RemoteDayRecord>): RemoteResult<Unit>

    /** Upsert on id, merged server-side by COALESCE(reversed_at). */
    suspend fun upsertCompletions(rows: List<RemoteCompletion>): RemoteResult<Unit>

    suspend fun upsertProfile(row: RemoteProfile): RemoteResult<Unit>

    // ---- reads ----

    /** Everything changed since [since], for the signed-in user, ascending by updated_at. */
    suspend fun changedSince(since: Instant?, limit: Int): RemoteResult<RemoteChanges>

    /** One backfill page: dates in [from, to], descending. Bounded and resumable (R11). */
    suspend fun recordsBetween(from: LocalDate, to: LocalDate): RemoteResult<RemoteChanges>

    /** The oldest date the account holds anything for, or null. Ends the backfill. */
    suspend fun earliestRecordedDate(): RemoteResult<LocalDate?>

    /** Published catalogue versions this app might understand. */
    suspend fun catalogues(knownFormatVersions: Set<Int>): RemoteResult<List<RemotePublication>>
}

data class RemoteChanges(
    val dayRecords: List<RemoteDayRecord>,
    val completions: List<RemoteCompletion>,
    val watermark: Instant?,   // server updated_at of the newest row in this batch
)

sealed interface RemoteResult<out T> {
    data class Ok<T>(val value: T) : RemoteResult<T>

    /** No connection, timeout, 5xx. Retry with backoff; never drop the entry (FR-021a). */
    data object Unreachable : RemoteResult<Nothing>

    /** The session is gone and could not be renewed. Caller signs out locally (FR-006). */
    data object NotAuthenticated : RemoteResult<Nothing>

    /** 4xx that a retry cannot fix — malformed row, unknown catalogue version, policy refusal. */
    data class Rejected(val reason: String, val entityIds: List<String>) : RemoteResult<Nothing>
}
```

## DTOs

`data/sync/dto/RemoteDtos.kt`. Serialisation shapes, mapped to and from domain types at this
boundary and nowhere else.

```kotlin
@Serializable
data class RemoteDayRecord(
    @SerialName("user_id") val userId: String,
    val date: String,                                   // ISO-8601
    @SerialName("catalogue_version") val catalogueVersion: Int,
    @SerialName("updated_at") val updatedAt: String? = null,   // server-assigned, read-only
)

@Serializable
data class RemoteCompletion(
    val id: String,                                     // the client-generated UUID
    @SerialName("user_id") val userId: String,
    @SerialName("credited_date") val creditedDate: String,
    @SerialName("task_slug") val taskSlug: String,
    @SerialName("points_awarded") val pointsAwarded: Int,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("reversed_at") val reversedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,   // server-assigned, read-only
)

@Serializable
data class RemoteProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class RemotePublication(
    val version: Int,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("format_version") val formatVersion: Int,
    val payload: String,                                // validated by :domain before any write
)
```

**`RemoteCompletion` carries no `day_plan_id`.** The local plan id is a device-local UUID and means
nothing on another device; ingest binds the completion to whatever plan the credited date has locally
(research R4).

## Guarantees the implementation must honour

1. **Every write is an upsert with an explicit conflict target.** `day_records` on
   `(user_id, date)`, `completions` on `id`, `profiles` on `id`, all with
   `Prefer: resolution=merge-duplicates`. Submitting the same row any number of times leaves one row
   and unchanged figures (FR-017, SC-005).
2. **A partial batch is reported, not swallowed.** `Rejected` names the entity ids that failed, so
   the engine can keep the rest moving and retry or surface those (FR-021, spec Edge Case "reachable
   but rejects a write").
3. **`Unreachable` is not an error state to the user.** It is the ordinary offline case and produces
   `SyncStatus.NotSyncing`, never a failure message (Principle IX).
4. **Nothing here reads a clock.** `updatedAt` is carried through as data. The engine's own
   timestamps come from `TimeProvider` (Principle VII).
5. **No method deletes.** There is no delete on the interface, none in the SQL policies, and none in
   the fake.

## `FakeRemoteDataSource`

Implements the Postgres semantics the engine depends on, and nothing more:

| Behaviour | Why the fake must have it |
|---|---|
| Upsert on the declared conflict target | otherwise idempotency tests prove nothing |
| `LEAST()` on `catalogue_version` | the day-record merge (R5) |
| `COALESCE()` on `reversed_at`, other fields frozen | the completion merge, and Principle III |
| Per-user row scoping | catches an engine that forgets `user_id`, though RLS proper is verified in SQL (R13) |
| Monotonic `updatedAt` | the pull cursor must advance deterministically |
| Injectable failure: unreachable, rejected row, mid-batch drop, ambiguous acknowledgement | US2 AS4/AS5, FR-021, and the "submitted more than once after an ambiguous failure" case |
| Injectable latency and page limits | backfill resumption (FR-023c) and SC-006 |

The fake is a test fixture, so it lives in `data/src/androidTest/…` beside the suites that use it,
and it is the only remote any test in this increment touches apart from the live contract test.
