package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.OutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * The change queue. **There is deliberately no method here that deletes by
 * age or caps the table** — a queued entry is the only copy of a fact the
 * user believes is recorded, and nothing may discard one (FR-021a).
 */
@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE nextAttemptAt <= :now ORDER BY createdAt LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id IN (:ids)")
    suspend fun remove(ids: List<String>)

    @Query("UPDATE outbox SET attempts = attempts + 1, nextAttemptAt = :at WHERE id IN (:ids)")
    suspend fun defer(ids: List<String>, at: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("DELETE FROM outbox")
    suspend fun clear()
}
