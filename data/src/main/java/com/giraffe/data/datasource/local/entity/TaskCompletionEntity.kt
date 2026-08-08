package com.giraffe.data.datasource.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "gregorianDateKey"],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class TaskCompletionEntity(
    val taskId: Long,
    val gregorianDateKey: String,
    val completedAt: Long
)
