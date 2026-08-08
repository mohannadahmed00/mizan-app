package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.TaskCompletion
import com.giraffe.domain.repository.HijriDateRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.any
import org.junit.jupiter.api.Test

class ToggleTaskCompletionUseCaseTest {

    private val hijriDateRepository: HijriDateRepository = mockk()
    private val useCase = ToggleTaskCompletionUseCase(hijriDateRepository)
    private val today = CompactDate(
        hijri = SimpleDate(1, 1, 1447),
        gregorian = SimpleDate(10, 7, 2026)
    )

    @Test
    fun `invoke inserts completion when none exists`() {
        val dateKey = "2026-07-10"
        coEvery { hijriDateRepository.getCompletionsForDate(dateKey) } returns emptyList()
        coEvery { hijriDateRepository.insertCompletion(1L, dateKey, any()) } returns Unit

        useCase(1L, today)

        coVerify { hijriDateRepository.insertCompletion(1L, dateKey, any()) }
    }

    @Test
    fun `invoke deletes completion when one already exists`() {
        val dateKey = "2026-07-10"
        coEvery { hijriDateRepository.getCompletionsForDate(dateKey) } returns listOf(
            TaskCompletion(1L, today, 1000L)
        )
        coEvery { hijriDateRepository.deleteCompletion(1L, dateKey) } returns Unit

        useCase(1L, today)

        coVerify { hijriDateRepository.deleteCompletion(1L, dateKey) }
    }
}
