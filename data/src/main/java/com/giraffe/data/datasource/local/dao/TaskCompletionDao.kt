package com.giraffe.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.data.datasource.local.entity.TaskCompletionEntity

@Dao
interface TaskCompletionDao {
    @Query("SELECT * FROM task_completions WHERE gregorianDateKey = :dateKey")
    suspend fun getCompletionsForDate(dateKey: String): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions")
    suspend fun getAllCompletions(): List<TaskCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND gregorianDateKey = :dateKey")
    suspend fun delete(taskId: Long, dateKey: String)
}
