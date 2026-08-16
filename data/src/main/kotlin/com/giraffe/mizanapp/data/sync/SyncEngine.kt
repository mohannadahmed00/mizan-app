package com.giraffe.mizanapp.data.sync

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import com.giraffe.mizanapp.data.mapper.toEntity
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.buildDayPlan
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.repository.CatalogueRepository
import com.giraffe.mizanapp.domain.sync.RetrySchedule
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
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
    private val catalogue: CatalogueRepository,
    private val time: TimeProvider,
    private val onSessionExpired: suspend () -> Unit = {},
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
        while (true) {
            val due = outbox.due(time.now(), limit = BATCH_LIMIT)
            if (due.isEmpty()) return DrainOutcome.Drained

            val dayRecords = due.filter { it.entityType == OutboxEntry.EntityType.DAY_RECORD }
            val completions = due.filter { it.entityType == OutboxEntry.EntityType.COMPLETION }
            val profiles = due.filter { it.entityType == OutboxEntry.EntityType.PROFILE }

            if (dayRecords.isNotEmpty()) {
                when (val outcome = sendDayRecords(dayRecords)) {
                    BatchOutcome.Unreachable -> return DrainOutcome.StoppedUnreachable
                    BatchOutcome.NotAuthenticated -> {
                        onSessionExpired()
                        return DrainOutcome.StoppedUnauthenticated
                    }
                    BatchOutcome.Continue -> Unit
                }
            }
            if (completions.isNotEmpty()) {
                when (val outcome = sendCompletions(completions)) {
                    BatchOutcome.Unreachable -> return DrainOutcome.StoppedUnreachable
                    BatchOutcome.NotAuthenticated -> {
                        onSessionExpired()
                        return DrainOutcome.StoppedUnauthenticated
                    }
                    BatchOutcome.Continue -> Unit
                }
            }
            if (profiles.isNotEmpty()) {
                when (val outcome = sendProfiles(profiles)) {
                    BatchOutcome.Unreachable -> return DrainOutcome.StoppedUnreachable
                    BatchOutcome.NotAuthenticated -> {
                        onSessionExpired()
                        return DrainOutcome.StoppedUnauthenticated
                    }
                    BatchOutcome.Continue -> Unit
                }
            }
        }
    }

    private suspend fun sendDayRecords(entries: List<OutboxEntry>): BatchOutcome {
        val rows = entries.map { Json.decodeFromString<RemoteDayRecord>(it.payload) }
        return when (val result = remote.upsertDayRecords(rows)) {
            is RemoteResult.Ok -> {
                _reachable.value = true
                accept(entries)
                val at = time.now().toEpochMilli()
                db.dayPlanDao().markSynced(entries.map { it.entityId }, at)
                BatchOutcome.Continue
            }
            RemoteResult.Unreachable -> {
                _reachable.value = false
                deferBatch(entries)
                BatchOutcome.Unreachable
            }
            RemoteResult.NotAuthenticated -> BatchOutcome.NotAuthenticated
            is RemoteResult.Rejected -> {
                _reachable.value = true
                rejectNamed(entries, result.entityIds) { accepted ->
                    db.dayPlanDao().markSynced(accepted.map { it.entityId }, time.now().toEpochMilli())
                }
                BatchOutcome.Continue
            }
        }
    }

    private suspend fun sendCompletions(entries: List<OutboxEntry>): BatchOutcome {
        val rows = entries.map { Json.decodeFromString<RemoteCompletion>(it.payload) }
        return when (val result = remote.upsertCompletions(rows)) {
            is RemoteResult.Ok -> {
                _reachable.value = true
                accept(entries)
                val at = time.now().toEpochMilli()
                db.completionDao().markSynced(entries.map { it.entityId }, at)
                BatchOutcome.Continue
            }
            RemoteResult.Unreachable -> {
                _reachable.value = false
                deferBatch(entries)
                BatchOutcome.Unreachable
            }
            RemoteResult.NotAuthenticated -> BatchOutcome.NotAuthenticated
            is RemoteResult.Rejected -> {
                _reachable.value = true
                rejectNamed(entries, result.entityIds) { accepted ->
                    db.completionDao().markSynced(accepted.map { it.entityId }, time.now().toEpochMilli())
                }
                BatchOutcome.Continue
            }
        }
    }

    private suspend fun sendProfiles(entries: List<OutboxEntry>): BatchOutcome {
        for (entry in entries) {
            val row = Json.decodeFromString<RemoteProfile>(entry.payload)
            when (val result = remote.upsertProfile(row)) {
                is RemoteResult.Ok -> {
                    _reachable.value = true
                    accept(listOf(entry))
                }
                RemoteResult.Unreachable -> {
                    _reachable.value = false
                    deferBatch(listOf(entry))
                    return BatchOutcome.Unreachable
                }
                RemoteResult.NotAuthenticated -> return BatchOutcome.NotAuthenticated
                is RemoteResult.Rejected -> {
                    _reachable.value = true
                    deferBatch(listOf(entry))
                }
            }
        }
        return BatchOutcome.Continue
    }

    private suspend fun accept(entries: List<OutboxEntry>) {
        outbox.accepted(entries.map { it.id })
    }

    private suspend fun deferBatch(entries: List<OutboxEntry>) {
        for (entry in entries) {
            outbox.deferred(listOf(entry.id), RetrySchedule.nextAttemptAt(entry.attempts, time.now()))
        }
    }

    /** Defers only the entries named in [rejectedEntityIds]; accepts and marks synced the rest. */
    private suspend fun rejectNamed(
        entries: List<OutboxEntry>,
        rejectedEntityIds: List<String>,
        markAcceptedSynced: suspend (List<OutboxEntry>) -> Unit,
    ) {
        val rejected = entries.filter { it.entityId in rejectedEntityIds }
        val accepted = entries - rejected.toSet()
        if (rejected.isNotEmpty()) deferBatch(rejected)
        if (accepted.isNotEmpty()) {
            accept(accepted)
            markAcceptedSynced(accepted)
        }
    }

    /**
     * First sign-in on a device: claim, enqueue, drain — each step idempotent
     * and safe to re-run alone (research R7), which is also what makes it
     * safe to run on *every* sync trigger rather than only the first. Step 1
     * of `contracts/sync-engine.md` §5 — pinning `account_scope` to [userId]
     * — is defensive here: the caller (`SupabaseAccountRepository.confirmCode`)
     * already set it with the real email before triggering sync.
     */
    suspend fun migrateOnSignIn(userId: String) {
        val current = accountScope.current()
        if (current !is AccountSession.SignedIn || current.userId != userId) {
            accountScope.set(userId, email = "", displayName = null)
        }
        claimLocalRecords(userId)
        enqueueUnsynced()
        drain()
    }

    /** No-op until T104 wires the real pull. Called by [SyncWorker] so its shape is stable from T083 on. */
    suspend fun pull() {
        // T104 fills this in.
    }

    /**
     * Ingests remote changes — `contracts/sync-engine.md` §6 — in one
     * transaction. Day records have exactly two outcomes: a local plan already
     * held for that date is left completely alone (FR-024a, Conventions §4),
     * otherwise a fresh plan is built at the incoming version. When that
     * version is not held locally the date is left `NOT_YET_KNOWN` rather than
     * guessed or dropped — a future catalogue pull is what resolves it.
     * Completions insert if absent and only ever apply a tombstone, never
     * rewriting `pointsAwarded`, `recordedAt`, `creditedDate` or `taskSlug`.
     */
    suspend fun applyRemote(changes: RemoteChanges) {
        db.withTransaction {
            for (row in changes.dayRecords) applyDayRecord(row)
            for (row in changes.completions) applyCompletionRow(row)
        }
    }

    private suspend fun applyDayRecord(row: RemoteDayRecord) {
        if (db.dayPlanDao().planByDate(row.date) != null) return // FR-024a: never touch an existing local day

        val version = row.catalogueVersion
        val content = catalogue.catalogueAt(version) ?: return // not held locally: stays NOT_YET_KNOWN

        val plan = buildDayPlan(
            content,
            version = version,
            date = LocalDate.parse(row.date),
            origin = PlanOrigin.BACKFILLED,
        ) { java.util.UUID.randomUUID().toString() }
        val now = time.now().toEpochMilli()
        db.dayPlanDao().insertPlanWithTasks(
            plan = plan.toEntity(now).copy(userId = row.userId, syncedAt = now),
            tasks = plan.plannedTasks.map { it.toEntity(now).copy(userId = row.userId) },
        )
    }

    private suspend fun applyCompletionRow(row: RemoteCompletion) {
        // dayPlanId is bound locally from creditedDate (research R4) — a
        // remote row carries none. If the date has not materialised locally
        // yet (its catalogue version is still unknown), the completion waits
        // for a later pull rather than binding to nothing.
        val localPlan = db.dayPlanDao().planByDate(row.creditedDate) ?: return

        val now = time.now().toEpochMilli()
        val entity = CompletionEntity(
            id = row.id,
            dayPlanId = localPlan.plan.id,
            taskSlug = row.taskSlug,
            creditedDate = row.creditedDate,
            pointsAwarded = row.pointsAwarded,
            recordedAt = Instant.parse(row.recordedAt).toEpochMilli(),
            reversedAt = row.reversedAt?.let { Instant.parse(it).toEpochMilli() },
            updatedAt = now,
            userId = row.userId,
            syncedAt = now,
        )
        db.completionDao().insertIgnoring(entity) // no-op if this id already exists locally
        entity.reversedAt?.let { db.completionDao().applyTombstone(entity.id, it) }
    }

    private companion object {
        const val BATCH_LIMIT = 200
    }
}

sealed interface DrainOutcome {
    data object Drained : DrainOutcome
    data object StoppedUnreachable : DrainOutcome
    data object StoppedUnauthenticated : DrainOutcome
}

private sealed interface BatchOutcome {
    data object Continue : BatchOutcome
    data object Unreachable : BatchOutcome
    data object NotAuthenticated : BatchOutcome
}
