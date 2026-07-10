package com.giraffe.domain.model

data class TaskCompletion(
    val taskId: Long,
    val date: CompactDate,
    val completedAt: Long
)