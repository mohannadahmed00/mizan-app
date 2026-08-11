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
}
