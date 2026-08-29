package com.giraffe.mizanapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entity.LeaderboardCacheEntity
import kotlinx.coroutines.flow.Flow

/** Provides the replaceable local snapshot observed by leaderboard surfaces. */
@Dao
interface LeaderboardCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LeaderboardCacheEntity)

    @Query("SELECT * FROM leaderboard_cache WHERE id = :id")
    fun observeById(id: String): Flow<LeaderboardCacheEntity?>

    @Query("DELETE FROM leaderboard_cache")
    suspend fun deleteAll()
}
