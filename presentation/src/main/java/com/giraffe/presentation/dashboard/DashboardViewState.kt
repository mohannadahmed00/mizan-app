package com.giraffe.presentation.dashboard

import com.giraffe.domain.model.Task

data class DashboardViewState(
    val gregorianDate: String = "",
    val hijriDate: String = "",
    val tasks: List<Task> = emptyList(),
    val completedTaskIds: Set<Long> = emptySet(),
    val completionPercent: Double = 0.0,
    val todayPoints: Int = 0,
    val isLoading: Boolean = true,
    val isNoTasksMessage: Boolean = false,
    val isAllComplete: Boolean = false
)
