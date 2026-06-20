package com.giraffe.domain.usecase

import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.provider.SystemDateProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SyncMonthlyHijriDatesUseCaseTest {

    private val systemDateProvider: SystemDateProvider = mockk()
    private val syncMonthlyHijriDates = SyncMonthlyHijriDatesUseCase(systemDateProvider)

    @Test
    fun `invoke calls systemDateProvider to get current date`() {
        // Given
        val todayGregorian = SimpleDate(1, 1, 2026)
        every { systemDateProvider.getCurrentGregorianDate() } returns todayGregorian

        // When
        syncMonthlyHijriDates(2026, 6)

        // Then
        verify { systemDateProvider.getCurrentGregorianDate() }
    }
}
