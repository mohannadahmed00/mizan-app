package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CategoryTest {

    @Test
    fun `verify all category values exist`() {
        val expectedValues = listOf(
            Category.FAJR,
            Category.DHUHR,
            Category.ASR,
            Category.MAGHRIB,
            Category.ISHA,
            Category.QURAN,
            Category.ADKAR,
            Category.FAST,
            Category.OTHER
        )
        
        val actualValues = Category.entries
        
        assertThat(actualValues).containsExactlyElementsIn(expectedValues).inOrder()
    }

    @Test
    fun `verify category count`() {
        assertThat(Category.entries).hasSize(9)
    }

    @Test
    fun `verify valueOf returns correct enum for each name`() {
        Category.entries.forEach { category ->
            assertThat(Category.valueOf(category.name)).isEqualTo(category)
        }
    }
}
