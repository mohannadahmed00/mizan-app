package com.giraffe.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar

class SystemDateProviderImplTest {

    private val provider = SystemDateProviderImpl()

    @Test
    fun `getCurrentGregorianDate returns current date components`() {
        // When
        val result = provider.getCurrentGregorianDate()
        
        // Then
        val calendar = Calendar.getInstance()
        assertThat(result.day).isEqualTo(calendar.get(Calendar.DAY_OF_MONTH))
        assertThat(result.month).isEqualTo(calendar.get(Calendar.MONTH) + 1)
        assertThat(result.year).isEqualTo(calendar.get(Calendar.YEAR))
    }
}
