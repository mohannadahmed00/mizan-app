package com.giraffe.data.datasource

import com.giraffe.domain.model.CompactDate

interface RemoteDataSource {
    /**
     * Hits the endpoint to fetch the 30/31 days for the given Gregorian month and year.
     */
    suspend fun fetchHijriDatesForMonth(year: Int, month: Int): List<CompactDate>
}