package com.giraffe.presentation.dashboard

import com.giraffe.domain.usecase.GetCurrentDateUseCase
import com.giraffe.domain.usecase.GetTodayStatsUseCase
import com.giraffe.domain.usecase.GetTodayTasksUseCase
import com.giraffe.domain.usecase.ToggleTaskCompletionUseCase
import com.giraffe.presentation.common.BaseViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DashboardViewModel(
    private val getCurrentDateUseCase: GetCurrentDateUseCase,
    private val getTodayTasksUseCase: GetTodayTasksUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
) : BaseViewModel<DashboardViewState, DashboardViewEffect>(DashboardViewState()) {

    fun loadDashboard() {
        tryToExecute(
            action = {
                val compactDate = getCurrentDateUseCase()
                val tasks = getTodayTasksUseCase()
                val stats = getTodayStatsUseCase()

                val gregorianDate = compactDate?.let {
                    "%04d-%02d-%02d".format(it.gregorian.year, it.gregorian.month, it.gregorian.day)
                } ?: ""

                val hijriDate = compactDate?.let {
                    "%04d-%02d-%02d".format(it.hijri.year, it.hijri.month, it.hijri.day)
                } ?: ""

                updateState {
                    copy(
                        gregorianDate = gregorianDate,
                        hijriDate = hijriDate,
                        tasks = tasks,
                        completedTaskIds = stats.completedTaskIds,
                        completionPercent = stats.completionPercent,
                        todayPoints = stats.todayPoints,
                        isLoading = false,
                        isNoTasksMessage = tasks.isEmpty(),
                        isAllComplete = stats.totalTasks > 0 &&
                            stats.completedTasks == stats.totalTasks
                    )
                }
            },
            onError = { DashboardViewEffect.ShowError(it.message ?: "Unknown error") }
        )
    }

    fun onTaskToggled(taskId: Long) {
        tryToExecute(
            action = {
                val compactDate = getCurrentDateUseCase() ?: return@tryToExecute
                toggleTaskCompletionUseCase(taskId, compactDate)
                val stats = getTodayStatsUseCase()
                updateState {
                    copy(
                        completedTaskIds = stats.completedTaskIds,
                        completionPercent = stats.completionPercent,
                        todayPoints = stats.todayPoints,
                        isAllComplete = stats.totalTasks > 0 &&
                            stats.completedTasks == stats.totalTasks
                    )
                }
            },
            onError = { DashboardViewEffect.ShowError(it.message ?: "Error toggling task") }
        )
    }
}
