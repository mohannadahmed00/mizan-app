package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.SyncCursorEntity

/** The three key-value sync cursors: `pull_cursor`, `backfill_floor`, `backfill_complete`. */
@Dao
interface SyncCursorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query("SELECT value FROM sync_cursors WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM sync_cursors")
    suspend fun clear()
}
