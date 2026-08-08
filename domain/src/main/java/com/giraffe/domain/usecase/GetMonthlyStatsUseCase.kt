package com.giraffe.domain.usecase

import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import java.time.LocalDate
import java.time.YearMonth

@Factory
class GetMonthlyStatsUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
) {
    suspend operator fun invoke(yearMonth: String): MonthlyStats {
        val parts = yearMonth.split("-")
        val year = parts[0].toIntOrNull() ?: return MonthlyStats(emptyMap(), 0.0, 0)
        val month = parts[1].toIntOrNull() ?: return MonthlyStats(emptyMap(), 0.0, 0)
        val yearMonthObj = YearMonth.of(year, month)
        val lastDay = yearMonthObj.lengthOfMonth()

        val tasks = hijriDateRepository.getTodayTasks()

        var totalPoints = 0
        val dailyPercents = mutableMapOf<String, Double>()

        for (day in 1..lastDay) {
            val dateKey = "%04d-%02d-%02d".format(year, month, day)
            val completions = hijriDateRepository.getCompletionsForDate(dateKey)
            val completedIds = completions.map { it.taskId }.toSet()
            val relevantTasks = tasks.filter { task ->
                val localDate = LocalDate.of(year, month, day)
                val dayOfWeek = localDate.dayOfWeek.name.take(2)
                task.activeDays.any { it.name == dayOfWeek }
            }
            if (relevantTasks.isNotEmpty()) {
                val completed = relevantTasks.count { it.id in completedIds }
                dailyPercents[dateKey] = completed.toDouble() / relevantTasks.size * 100.0
            } else {
                dailyPercents[dateKey] = 0.0
            }
            totalPoints += completions.filter { it.taskId in tasks.map { t -> t.id } }
                .sumOf { c -> tasks.find { it.id == c.taskId }?.points ?: 0 }
        }

        val monthlyAverage = if (dailyPercents.isEmpty()) 0.0
            else dailyPercents.values.average()

        return MonthlyStats(
            dailyPercents = dailyPercents,
            monthlyAverage = monthlyAverage,
            monthlyPoints = totalPoints,
        )
    }
}
