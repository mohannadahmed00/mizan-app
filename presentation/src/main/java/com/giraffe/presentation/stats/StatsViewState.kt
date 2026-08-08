package com.giraffe.presentation.stats

data class StatsViewState(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val dailyPercent: Double = 0.0,
    val weeklyPercent: Double = 0.0,
    val monthlyPercent: Double = 0.0,
    val totalPoints: Int = 0,
    val isLoading: Boolean = true
)
