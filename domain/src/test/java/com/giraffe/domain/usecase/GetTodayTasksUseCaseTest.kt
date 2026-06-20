package com.giraffe.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GetTodayTasksUseCaseTest {

    @Test
    fun `invoke returns empty list for now`() {
        val result = GetTodayTasksUseCase().invoke()
        assertThat(result).isEmpty()
    }
}
