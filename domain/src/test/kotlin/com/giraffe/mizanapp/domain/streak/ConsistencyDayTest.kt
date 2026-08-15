package com.giraffe.mizanapp.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsistencyDayTest {

    private val date = LocalDate.parse("2026-08-19")

    @Test
    fun date_in_the_set_counts() {
        assertTrue(isConsistencyDay(date, setOf(date, LocalDate.parse("2026-08-18"))))
    }

    @Test
    fun date_not_in_the_set_does_not_count() {
        assertFalse(isConsistencyDay(date, setOf(LocalDate.parse("2026-08-18"))))
    }

    @Test
    fun empty_set_never_counts() {
        assertFalse(isConsistencyDay(date, emptySet()))
    }
}
