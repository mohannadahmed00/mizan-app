package com.giraffe.domain.usecase

data class TodayStats(
    val totalTasks: Int,
    val completedTasks: Int,
    val completionPercent: Double,
    val todayPoints: Int,
    val completedTaskIds: Set<Long> = emptySet(),
)
