package com.giraffe.domain.usecase

import com.giraffe.domain.model.Category
import com.giraffe.domain.model.Day
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.Task
import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.HijriDateRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class GetTodayTasksUseCaseTest {

    private val hijriDateRepository: HijriDateRepository = mockk()
    private val systemDateProvider: SystemDateProvider = mockk()
    private val useCase = GetTodayTasksUseCase(hijriDateRepository, systemDateProvider)

    @Test
    fun `invoke returns tasks filtered by today day-of-week`() {
        val today = SimpleDate(day = 10, month = 7, year = 2026)
        every { systemDateProvider.getCurrentGregorianDate() } returns today

        val allTasks = listOf(
            Task(1, "Fajr", Category.FAJR, 5, setOf(Day.FR)),
            Task(2, "Dhuhr", Category.DHUHR, 5, setOf(Day.SA)),
        )
        coEvery { hijriDateRepository.getTodayTasks() } returns allTasks

        val result = useCase()

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Fajr")
    }

    @Test
    fun `invoke returns empty list when repository returns empty`() {
        val today = SimpleDate(day = 10, month = 7, year = 2026)
        every { systemDateProvider.getCurrentGregorianDate() } returns today
        coEvery { hijriDateRepository.getTodayTasks() } returns emptyList()

        val result = useCase()

        assertThat(result).isEmpty()
    }
}
