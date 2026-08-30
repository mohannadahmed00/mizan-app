package com.giraffe.mizanapp.data.db.daos
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import kotlinx.coroutines.flow.Flow
@Dao interface BoundaryStateDao { @Query("SELECT * FROM boundary_state WHERE id = 0") suspend fun get(): BoundaryStateEntity?; @Query("SELECT * FROM boundary_state WHERE id = 0") fun observe(): Flow<BoundaryStateEntity?>; @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: BoundaryStateEntity) }
