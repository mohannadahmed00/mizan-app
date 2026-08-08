package com.giraffe.domain.usecase

data class MonthlyStats(
    val dailyPercents: Map<String, Double>,
    val monthlyAverage: Double,
    val monthlyPoints: Int,
)
