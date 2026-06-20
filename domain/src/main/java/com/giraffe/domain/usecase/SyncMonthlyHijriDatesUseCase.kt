package com.giraffe.domain.usecase

import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class SyncMonthlyHijriDatesUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
    @Provided private val systemDateProvider: SystemDateProvider,
) {
    suspend operator fun invoke() {
        val currentDate = systemDateProvider.getCurrentGregorianDate()
        repeat(2) { index ->
            hijriDateRepository.syncMonthlyHijriDates(
                month = currentDate.month + index,
                year = currentDate.year
            )
        }
    }
}