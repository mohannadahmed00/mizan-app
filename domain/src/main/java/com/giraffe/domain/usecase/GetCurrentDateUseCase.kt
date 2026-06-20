package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.provider.SystemDateProvider
import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class GetCurrentDateUseCase(
    @Provided private val hijriDateRepository: HijriDateRepository,
    @Provided private val systemDateProvider: SystemDateProvider,
) {
    suspend operator fun invoke(): CompactDate {
        val currentDate = systemDateProvider.getCurrentGregorianDate()
        var compactDate = hijriDateRepository.getCompactDateOf(currentDate)
        if (compactDate == null) {
            hijriDateRepository.syncMonthlyHijriDates(
                currentDate.month,
                currentDate.year
            )
            compactDate = hijriDateRepository.getCompactDateOf(currentDate)
        }
        return requireNotNull(compactDate)
    }
}