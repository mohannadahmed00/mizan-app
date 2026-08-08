package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class ToggleTaskCompletionUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
) {
    suspend operator fun invoke(taskId: Long, date: CompactDate) {
        val dateKey = "%04d-%02d-%02d".format(
            date.gregorian.year, date.gregorian.month, date.gregorian.day
        )
        val existing = hijriDateRepository.getCompletionsForDate(dateKey)
        val alreadyCompleted = existing.any { it.taskId == taskId }
        if (alreadyCompleted) {
            hijriDateRepository.deleteCompletion(taskId, dateKey)
        } else {
            hijriDateRepository.insertCompletion(taskId, dateKey, System.currentTimeMillis())
        }
    }
}
