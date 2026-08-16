package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.OutboxEntity
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One queued change, as the engine sees it. The Room row is [OutboxEntity];
 * this is its in-memory form, with [id] derived rather than supplied so a
 * caller cannot get it wrong.
 */
data class OutboxEntry(
    val entityType: EntityType,
    val entityId: String,
    val operation: Operation,
    val payload: String,
    val attempts: Int = 0,
) {
    val id: String get() = "${entityType.wire}:$entityId:${operation.wire}"

    enum class EntityType(val wire: String) {
        COMPLETION("completion"),
        DAY_RECORD("day_record"),
        PROFILE("profile"),
    }

    /** There is no DELETE: an undo is a tombstone carried by an UPSERT (FR-018). */
    enum class Operation(val wire: String) { UPSERT("UPSERT") }
}

/**
 * The change queue. **There is deliberately no method here that expires, caps,
 * or evicts an entry** — a queued change is the only copy of a fact the user
 * believes is recorded (FR-021a).
 */
class Outbox(private val db: MizanDatabase, private val time: TimeProvider) {

    /** Called inside the caller's transaction. Deterministic id, so this is idempotent (R6). */
    suspend fun enqueue(entry: OutboxEntry) {
        val now = time.now().toEpochMilli()
        db.outboxDao().upsert(
            OutboxEntity(
                id = entry.id,
                entityType = entry.entityType.wire,
                entityId = entry.entityId,
                operation = entry.operation.wire,
                payload = entry.payload,
                createdAt = now,
                attempts = entry.attempts,
                nextAttemptAt = now,
            ),
        )
    }

    suspend fun due(now: Instant, limit: Int): List<OutboxEntry> =
        db.outboxDao().due(now.toEpochMilli(), limit).map { it.toEntry() }

    /** The only path that removes an entry — a change reaches this once the account has it. */
    suspend fun accepted(ids: List<String>) {
        db.outboxDao().remove(ids)
    }

    suspend fun deferred(ids: List<String>, at: Instant) {
        db.outboxDao().defer(ids, at.toEpochMilli())
    }

    fun observePendingCount(): Flow<Int> = db.outboxDao().observeCount()

    private fun OutboxEntity.toEntry() = OutboxEntry(
        entityType = OutboxEntry.EntityType.entries.first { it.wire == entityType },
        entityId = entityId,
        operation = OutboxEntry.Operation.entries.first { it.wire == operation },
        payload = payload,
        attempts = attempts,
    )
}
