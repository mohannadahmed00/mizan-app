package com.giraffe.mizanapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import kotlinx.coroutines.flow.Flow

/** Keeps the current account's remote consent state isolated and replaceable. */
@Dao
interface ParticipationStateDao {

    @Query("SELECT * FROM participation_state WHERE id = 1")
    fun observe(): Flow<ParticipationStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ParticipationStateEntity)

    @Query("DELETE FROM participation_state")
    suspend fun deleteAll()
}
