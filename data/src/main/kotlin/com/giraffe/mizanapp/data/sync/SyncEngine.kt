package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.domain.time.TimeProvider

/**
 * The four responsibilities of sync — claim, enqueue, drain, and the sequence
 * that chains them on first sign-in — plus, in later phases, pull and
 * backfill. See `specs/007-identity-cloud-sync/contracts/sync-engine.md`.
 *
 * **Nothing here may sit on the path of viewing tasks, recording a
 * completion, undoing one, or computing a score** (Principle IV, FR-014).
 */
class SyncEngine(
    private val db: MizanDatabase,
    private val outbox: Outbox,
    private val accountScope: AccountScope,
    private val remote: RemoteDataSource,
    private val time: TimeProvider,
) {

    /** Attributes every unclaimed local row to [userId], inside one transaction. Deletes nothing (FR-010). */
    suspend fun claimLocalRecords(userId: String) {
        TODO("T063")
    }

    /** Enqueues every not-yet-synced local row, oldest date first. Idempotent by [OutboxEntry.id]. */
    suspend fun enqueueUnsynced() {
        TODO("T064")
    }

    /** Sends every due outbox entry, per `contracts/sync-engine.md` §2. */
    suspend fun drain(): DrainOutcome {
        TODO("T065")
    }

    /** First sign-in on a device: claim, enqueue, drain, pull — each step safe to re-run alone (research R7). */
    suspend fun migrateOnSignIn(userId: String) {
        TODO("T066")
    }

    /** No-op until T104 wires the real pull. Called by [SyncWorker] so its shape is stable from T083 on. */
    suspend fun pull() {
        // T104 fills this in.
    }
}

sealed interface DrainOutcome {
    data object Drained : DrainOutcome
    data object StoppedUnreachable : DrainOutcome
    data object StoppedUnauthenticated : DrainOutcome
}
