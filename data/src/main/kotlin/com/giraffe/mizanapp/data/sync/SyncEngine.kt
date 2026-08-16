package com.giraffe.mizanapp.data.sync

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

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

    private val _reachable = MutableStateFlow(true)

    /** The last-observed reachability of the account, updated by every [drain] attempt. */
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    /** Attributes every unclaimed local row to [userId], inside one transaction. Deletes nothing (FR-010). */
    suspend fun claimLocalRecords(userId: String) {
        val at = time.now().toEpochMilli()
        db.withTransaction {
            db.dayPlanDao().claimPlansForUser(userId, at)
            db.dayPlanDao().claimPlannedTasksForUser(userId, at)
            db.completionDao().claimForUser(userId, at)
        }
    }

    /** Enqueues every not-yet-synced local row, oldest date first. Idempotent by [OutboxEntry.id]. */
    suspend fun enqueueUnsynced() {
        for (plan in db.dayPlanDao().unsynced()) {
            val userId = plan.userId ?: continue
            val payload = Json.encodeToString(
                RemoteDayRecord(userId = userId, date = plan.date, catalogueVersion = plan.catalogueVersion),
            )
            outbox.enqueue(
                OutboxEntry(
                    entityType = OutboxEntry.EntityType.DAY_RECORD,
                    entityId = plan.date,
                    operation = OutboxEntry.Operation.UPSERT,
                    payload = payload,
                ),
            )
        }
        for (completion in db.completionDao().unsynced()) {
            val userId = completion.userId ?: continue
            val payload = Json.encodeToString(
                RemoteCompletion(
                    id = completion.id,
                    userId = userId,
                    creditedDate = completion.creditedDate,
                    taskSlug = completion.taskSlug,
                    pointsAwarded = completion.pointsAwarded,
                    recordedAt = Instant.ofEpochMilli(completion.recordedAt).toString(),
                    reversedAt = completion.reversedAt?.let { Instant.ofEpochMilli(it).toString() },
                ),
            )
            outbox.enqueue(
                OutboxEntry(
                    entityType = OutboxEntry.EntityType.COMPLETION,
                    entityId = completion.id,
                    operation = OutboxEntry.Operation.UPSERT,
                    payload = payload,
                ),
            )
        }
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
