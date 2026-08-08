package com.giraffe.domain.usecase

import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class GetTodayStatsUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
) {
    suspend operator fun invoke(): TodayStats {
        val tasks = hijriDateRepository.getTodayTasks()
        val todayDate = "%04d-%02d-%02d".format(
            java.time.LocalDate.now().year,
            java.time.LocalDate.now().monthValue,
            java.time.LocalDate.now().dayOfMonth
        )
        val completions = hijriDateRepository.getCompletionsForDate(todayDate)
        val completedIds = completions.map { it.taskId }.toSet()
        val completedToday = tasks.filter { it.id in completedIds }

        return TodayStats(
            totalTasks = tasks.size,
            completedTasks = completedToday.size,
            completionPercent = if (tasks.isEmpty()) 0.0
                else completedToday.size.toDouble() / tasks.size * 100.0,
            todayPoints = completedToday.sumOf { it.points },
            completedTaskIds = completedIds,
        )
    }
}
