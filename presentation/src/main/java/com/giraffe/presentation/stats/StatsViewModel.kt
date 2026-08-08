package com.giraffe.presentation.stats

import com.giraffe.domain.usecase.GetMonthlyStatsUseCase
import com.giraffe.domain.usecase.GetStreaksUseCase
import com.giraffe.domain.usecase.GetTodayStatsUseCase
import com.giraffe.presentation.common.BaseViewModel
import java.time.LocalDate
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class StatsViewModel(
    private val getStreaksUseCase: GetStreaksUseCase,
    private val getTodayStatsUseCase: GetTodayStatsUseCase,
    private val getMonthlyStatsUseCase: GetMonthlyStatsUseCase,
) : BaseViewModel<StatsViewState, StatsViewEffect>(StatsViewState()) {

    fun loadStats() {
        tryToExecute(
            action = {
                val streakData = getStreaksUseCase()
                val todayStats = getTodayStatsUseCase()
                val now = LocalDate.now()
                val yearMonth = "%04d-%02d".format(now.year, now.monthValue)
                val monthlyStats = getMonthlyStatsUseCase(yearMonth)
                val weeklyPercent = computeWeeklyPercent()

                updateState {
                    copy(
                        currentStreak = streakData.currentStreak,
                        longestStreak = streakData.longestStreak,
                        dailyPercent = todayStats.completionPercent,
                        weeklyPercent = weeklyPercent,
                        monthlyPercent = monthlyStats.monthlyAverage,
                        totalPoints = monthlyStats.monthlyPoints,
                        isLoading = false,
                    )
                }
            },
            onError = { StatsViewEffect.ShowError(it.message ?: "Unknown error") }
        )
    }

    private suspend fun computeWeeklyPercent(): Double {
        val todayStats = getTodayStatsUseCase()
        return todayStats.completionPercent
    }
}
