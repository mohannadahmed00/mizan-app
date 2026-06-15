package com.giraffe.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GetCurrentDateUseCaseTest {

    @Test
    fun `invoke returns correct fixed current date`() {
        val result = GetCurrentDateUseCase().invoke()

        assertThat(result.hijri.year).isEqualTo(1447)
        assertThat(result.gregorian.year).isEqualTo(2026)
    }
}
