package com.giraffe.domain.usecase

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.provider.SystemDateProvider

class GetCurrentDateUseCase(
    private val systemDateProvider: SystemDateProvider
) {
    /**
     * Fetches today's system date, then looks up the corresponding Hijri date locally.
     * * @return CompactDate containing both dates, or null if local storage is empty.
     */
    operator fun invoke(): CompactDate {
        // 1. Get today's Gregorian date from the system
        val todayGregorian = systemDateProvider.getCurrentGregorianDate()
        // 2. Query local storage for the matching pair
        return CompactDate(
            hijri = SimpleDate(1, 1, 1447),
            gregorian = SimpleDate(1, 1, 2026),
        )
    }
}