package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.TaskCompletion
import com.giraffe.domain.repository.HijriDateRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test

class GetStreaksUseCaseTest {

    private val hijriDateRepository: HijriDateRepository = mockk()
    private val useCase = GetStreaksUseCase(hijriDateRepository)

    @Test
    fun `current streak counts consecutive days ending today`() {
        val today = currentDate()
        val yesterday = today.minusDays(1)
        coEvery { hijriDateRepository.getAllCompletions() } returns listOf(
            completion(today),
            completion(yesterday),
        )

        val result = useCase()

        assertThat(result.currentStreak).isAtLeast(2)
    }

    @Test
    fun `current streak resets after a missed day`() {
        val today = currentDate()
        val twoDaysAgo = today.minusDays(2)
        coEvery { hijriDateRepository.getAllCompletions() } returns listOf(
            completion(today),
            completion(twoDaysAgo),
        )

        val result = useCase()

        assertThat(result.currentStreak).isEqualTo(1)
    }

    @Test
    fun `longest streak exceeds current streak`() {
        val today = currentDate()
        val yesterday = today.minusDays(1)
        val oldDay1 = today.minusDays(10)
        val oldDay2 = today.minusDays(11)
        val oldDay3 = today.minusDays(12)
        coEvery { hijriDateRepository.getAllCompletions() } returns listOf(
            completion(today),
            completion(yesterday),
            completion(oldDay1),
            completion(oldDay2),
            completion(oldDay3),
        )

        val result = useCase()

        assertThat(result.longestStreak).isAtLeast(3)
        assertThat(result.longestStreak).isAtLeast(result.currentStreak)
    }

    @Test
    fun `empty completions returns zero for both`() {
        coEvery { hijriDateRepository.getAllCompletions() } returns emptyList()

        val result = useCase()

        assertThat(result.currentStreak).isEqualTo(0)
        assertThat(result.longestStreak).isEqualTo(0)
    }

    private fun currentDate() = java.time.LocalDate.now()

    private fun completion(date: java.time.LocalDate): TaskCompletion = TaskCompletion(
        taskId = 1L,
        date = CompactDate(
            hijri = SimpleDate(date.dayOfMonth, date.monthValue, date.year),
            gregorian = SimpleDate(date.dayOfMonth, date.monthValue, date.year),
        ),
        completedAt = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
}
