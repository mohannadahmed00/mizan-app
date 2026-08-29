package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemotePublication
import java.time.Instant
import java.time.LocalDate

/**
 * The single seam between the sync engine and the account backend.
 *
 * **No Supabase or Ktor type appears here.** They exist only inside
 * `SupabaseRemoteDataSource` and `SupabaseClientFactory`, which are the only
 * two files in the repository allowed to import them. No method throws — every
 * outcome, including offline and rejection, is a [RemoteResult] value.
 */
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
    val watermark: Instant?,
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
