package com.giraffe.domain.usecase

import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.HijriDateRepository
import io.mockk.mockk

class SyncMonthlyHijriDatesUseCaseTest {
    private val hijriDateRepository: HijriDateRepository = mockk()
    private val systemDateProvider: SystemDateProvider = mockk()
    private val syncMonthlyHijriDates =
        SyncMonthlyHijriDatesUseCase(hijriDateRepository, systemDateProvider)
}