package com.giraffe.domain.usecase

import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class GetStreaksUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
) {
    suspend operator fun invoke(): StreakData {
        val allCompletions = hijriDateRepository.getAllCompletions()
        if (allCompletions.isEmpty()) return StreakData(0, 0)

        val datesWithCompletion = allCompletions
            .map { it.date.gregorian }
            .distinct()
            .sortedWith(compareBy({ it.year }, { it.month }, { it.day }))

        val dates = datesWithCompletion.map {
            java.time.LocalDate.of(it.year, it.month, it.day)
        }

        if (dates.isEmpty()) return StreakData(0, 0)

        var currentStreak = 0
        var longestStreak = 0
        var runLength = 1

        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        for (i in 1 until dates.size) {
            if (dates[i] == dates[i - 1].plusDays(1)) {
                runLength++
            } else {
                longestStreak = maxOf(longestStreak, runLength)
                runLength = 1
            }
        }
        longestStreak = maxOf(longestStreak, runLength)

        if (dates.last() == today || dates.last() == yesterday) {
            var streak = 1
            for (i in dates.indices.reversed().drop(1)) {
                if (dates[i + 1] == dates[i].plusDays(1)) {
                    streak++
                } else {
                    break
                }
            }
            currentStreak = streak
        }

        return StreakData(currentStreak, longestStreak)
    }
}
