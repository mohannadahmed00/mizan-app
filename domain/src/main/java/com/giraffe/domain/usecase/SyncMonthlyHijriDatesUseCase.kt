package com.giraffe.domain.usecase

import com.giraffe.domain.provider.SystemDateProvider

class SyncMonthlyHijriDatesUseCase (
    private val systemDateProvider: SystemDateProvider
){
    /**
     * @param targetYear The Gregorian year (e.g., 2026)
     * @param targetMonth The Gregorian month (e.g., 6 for June)
     */
    operator fun invoke(targetYear: Int, targetMonth: Int) {
        val todayGregorian = systemDateProvider.getCurrentGregorianDate()
        // 1. Fetch data from the endpoint
        // 2. Validate the payload isn't empty before wiping/updating local storage
    }
}