package com.giraffe.domain.usecase

import com.giraffe.domain.repository.Repository
import io.mockk.mockk

class SyncMonthlyHijriDatesUseCaseTest {
    private val repository: Repository = mockk()
    private val syncMonthlyHijriDates = SyncMonthlyHijriDatesUseCase(repository)
}
