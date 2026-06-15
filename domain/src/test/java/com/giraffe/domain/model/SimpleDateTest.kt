package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SimpleDateTest {

    @Test
    fun `date correctly stores day month and year`() {
        val date = SimpleDate(day = 15, month = 5, year = 2024)
        
        assertThat(date.day).isEqualTo(15)
        assertThat(date.month).isEqualTo(5)
        assertThat(date.year).isEqualTo(2024)
    }
}
