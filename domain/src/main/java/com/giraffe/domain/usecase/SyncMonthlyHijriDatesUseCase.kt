package com.giraffe.domain.usecase

import com.giraffe.domain.repository.Repository
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided

@Factory
class SyncMonthlyHijriDatesUseCase(
    @Provided private val repository: Repository,
) {
    suspend operator fun invoke() {
        // 1. Fetch data from the endpoint
        repository.syncMonthlyHijriDates()
        // 2. Validate the payload isn't empty before wiping/updating local storage
    }
}