package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TaskTest {

    @Test
    fun `task correctly stores all properties`() {
        val activeDays = Day.entries.toSet()
        val task = Task(
            id = 1L,
            name = "Pray Fajr",
            category = Category.FAJR,
            points = 10,
            activeDays = activeDays
        )
        
        assertThat(task.id).isEqualTo(1L)
        assertThat(task.name).isEqualTo("Pray Fajr")
        assertThat(task.category).isEqualTo(Category.FAJR)
        assertThat(task.points).isEqualTo(10)
        assertThat(task.activeDays).isEqualTo(activeDays)
    }
}
