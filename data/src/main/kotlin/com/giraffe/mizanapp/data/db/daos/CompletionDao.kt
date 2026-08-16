package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * The append-only completion log.
 *
 * **Every read filters `reversedAt IS NULL`.** Undo writes a tombstone rather
 * than deleting, so a query that forgot this filter would count reversed
 * records against an occurrence limit — locking a nine-occurrence task at nine
 * after one mistaken tap, permanently and with no visible reason.
 */
@Dao
interface CompletionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(completion: CompletionEntity)

    @Query(
        "SELECT * FROM completions " +
            "WHERE creditedDate = :date AND reversedAt IS NULL AND deletedAt IS NULL " +
            "ORDER BY recordedAt"
    )
    fun observeLiveByDate(date: String): Flow<List<CompletionEntity>>

    @Query(
        "SELECT * FROM completions " +
            "WHERE creditedDate = :date AND reversedAt IS NULL AND deletedAt IS NULL " +
            "ORDER BY recordedAt"
    )
    suspend fun liveByDate(date: String): List<CompletionEntity>

    @Query(
        "SELECT COUNT(*) FROM completions " +
            "WHERE creditedDate = :date AND taskSlug = :slug " +
            "AND reversedAt IS NULL AND deletedAt IS NULL"
    )
    suspend fun liveCount(date: String, slug: String): Int

    @Query(
        "SELECT * FROM completions " +
            "WHERE creditedDate = :date AND taskSlug = :slug " +
            "AND reversedAt IS NULL AND deletedAt IS NULL " +
            "ORDER BY recordedAt DESC LIMIT 1"
    )
    suspend fun latestLive(date: String, slug: String): CompletionEntity?

    /** Tombstones the single most recent live record. Never deletes. */
    @Query("UPDATE completions SET reversedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun reverseById(id: String, at: Long): Int

    /** Includes tombstones. For durability tests and a future sync path only. */
    @Query("SELECT COUNT(*) FROM completions WHERE creditedDate = :date")
    suspend fun countAllRows(date: String): Int

    /** Includes tombstones. For read-only-audit tests only — no UI path uses this. */
    @Query("SELECT * FROM completions WHERE creditedDate BETWEEN :start AND :end ORDER BY creditedDate, recordedAt")
    suspend fun allBetween(start: String, end: String): List<CompletionEntity>

    /**
     * The week's live completions. Filters tombstones exactly like every
     * other read here — a range read that forgot this would inflate a past
     * week's earned total.
     */
    @Query(
        "SELECT * FROM completions " +
            "WHERE creditedDate BETWEEN :start AND :end AND reversedAt IS NULL AND deletedAt IS NULL " +
            "ORDER BY creditedDate, recordedAt"
    )
    suspend fun liveBetween(start: String, end: String): List<CompletionEntity>

    /**
     * Every date carrying at least one live completion, ascending and distinct.
     * Covered by the existing `creditedDate` index — no new index is added.
     */
    @Query(
        "SELECT DISTINCT creditedDate FROM completions " +
            "WHERE reversedAt IS NULL AND deletedAt IS NULL ORDER BY creditedDate"
    )
    fun observeLiveDates(): Flow<List<String>>

    /**
     * Attributes every unclaimed local completion to [userId] on sign-in.
     *
     * The `IS NULL` guard is what makes this idempotent and what stops a
     * record ever changing accounts once claimed (FR-013): re-running it after
     * the first sign-in touches nothing, and it can never move a row from one
     * user to another.
     */
    @Query("UPDATE completions SET userId = :userId, updatedAt = :at WHERE userId IS NULL")
    suspend fun claimForUser(userId: String, at: Long): Int

    @Query("UPDATE completions SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)

    /** Every row, including tombstones. Feeds the account-switch confirmation (FR-013a). */
    @Query("SELECT COUNT(*) FROM completions")
    suspend fun countAll(): Int

    @Query("SELECT * FROM completions WHERE syncedAt IS NULL")
    suspend fun unsynced(): List<CompletionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(row: CompletionEntity): Long

    /**
     * Applies an incoming tombstone. Can only ever *set* [CompletionEntity.reversedAt] —
     * the `reversedAt IS NULL` guard means a live local copy can be tombstoned by
     * a remote one, but a remote row can never clear a tombstone already applied
     * locally (FR-018).
     */
    @Query(
        "UPDATE completions SET reversedAt = :at, updatedAt = :at " +
            "WHERE id = :id AND reversedAt IS NULL"
    )
    suspend fun applyTombstone(id: String, at: Long)

    @Query("DELETE FROM completions")
    suspend fun clear()
}
