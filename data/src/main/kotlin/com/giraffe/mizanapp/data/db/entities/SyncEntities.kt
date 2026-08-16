package com.giraffe.mizanapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One change waiting for the account to accept it.
 *
 * [id] is the deterministic `"$entityType:$entityId:$operation"` — enqueueing
 * the same change twice replaces the payload rather than duplicating a row,
 * which is what makes `Outbox.enqueue` idempotent (R6). **There is deliberately
 * no column and no DAO method that expires, caps, or evicts a row here**
 * (FR-021a) — a queued entry is the only copy of a fact the user believes is
 * recorded.
 */
@Entity(tableName = "outbox", indices = [Index("nextAttemptAt")])
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
    val nextAttemptAt: Long,
)

/**
 * A key-value sync cursor: `pull_cursor`, `backfill_floor`, `backfill_complete`.
 *
 * Key-value rather than typed columns because each cursor is written
 * independently and a partially-written row must not be representable.
 */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * The one account this device currently carries records for. Exactly one row,
 * pinned to id 0. A null [userId] means the device has never held an account's
 * records — the state every fresh install and every plain sign-out returns to.
 */
@Entity(tableName = "account_scope")
data class AccountScopeEntity(
    @PrimaryKey val id: Int = 0,
    val userId: String?,
    val email: String?,
    val displayName: String?,
    val updatedAt: Long,
)
