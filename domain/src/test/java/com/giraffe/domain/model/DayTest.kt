package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayTest {

    @Test
    fun `verify all day values exist`() {
        val expectedDays = listOf(
            Day.SA, Day.SU, Day.MO, Day.TU, Day.WE, Day.TH, Day.FR
        )
        
        assertThat(Day.entries).containsExactlyElementsIn(expectedDays).inOrder()
    }
}
