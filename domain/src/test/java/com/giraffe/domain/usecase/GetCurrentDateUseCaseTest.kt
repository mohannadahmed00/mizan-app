package com.giraffe.domain.usecase

import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.provider.SystemDateProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class GetCurrentDateUseCaseTest {

    private val systemDateProvider: SystemDateProvider = mockk()
    private val getCurrentDateUseCase = GetCurrentDateUseCase(systemDateProvider)

    @Test
    fun `invoke returns correct compact date based on system date`() {
        // Given
        val todayGregorian = SimpleDate(1, 1, 2026)
        every { systemDateProvider.getCurrentGregorianDate() } returns todayGregorian

        // When
        val result = getCurrentDateUseCase()

        // Then
        assertThat(result.hijri.year).isEqualTo(1447)
        assertThat(result.gregorian).isEqualTo(todayGregorian)
    }
}
