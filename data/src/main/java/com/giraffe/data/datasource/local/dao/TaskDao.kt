package com.giraffe.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.data.datasource.local.entity.TaskEntity

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isActive = 1")
    suspend fun getAllActive(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getById(taskId: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
