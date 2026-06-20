package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CompactDateTest {

    @Test
    fun `compact date correctly stores hijri and gregorian dates`() {
        val hijri = SimpleDate(1, 9, 1445)
        val gregorian = SimpleDate(11, 3, 2024)
        
        val compactDate = CompactDate(hijri, gregorian)
        
        assertThat(compactDate.hijri).isEqualTo(hijri)
        assertThat(compactDate.gregorian).isEqualTo(gregorian)
    }
}
