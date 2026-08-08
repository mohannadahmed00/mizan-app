package com.giraffe.domain.usecase

import com.giraffe.domain.model.Day
import com.giraffe.domain.model.Task
import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import java.time.LocalDate

@Factory
class GetTodayTasksUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
    @Provided private val systemDateProvider: SystemDateProvider,
) {
    suspend operator fun invoke(): List<Task> {
        val today = systemDateProvider.getCurrentGregorianDate()
        val todayDayOfWeek = Day.valueOf(
            LocalDate.of(today.year, today.month, today.day)
                .dayOfWeek.name.take(2)
        )
        return hijriDateRepository.getTodayTasks()
            .filter { it.activeDays.contains(todayDayOfWeek) }
    }
}
