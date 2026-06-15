package com.giraffe.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TaskCompletionTest {

    @Test
    fun `task completion correctly stores task id and date`() {
        val date = CompactDate(Date(1, 1, 1445), Date(1, 1, 2024))
        val taskCompletion = TaskCompletion(taskId = 100L, date = date)
        
        assertThat(taskCompletion.taskId).isEqualTo(100L)
        assertThat(taskCompletion.date).isEqualTo(date)
    }
}
