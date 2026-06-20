package com.giraffe.data.datasource

import com.giraffe.data.response.MonthlyHijriDatesResponse
import org.koin.core.annotation.Singleton
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@Singleton
interface RemoteDataSource {
    /**
     * Hits the endpoint to fetch the 30/31 days for the given Gregorian month and year.
     */
    @GET("gToHCalendar/{month}/{year}?calendarMethod=UAQ")
    suspend fun fetchHijriDatesForMonth(
        @Path("month") month: Int,
        @Path("year") year: Int,
    ): Response<MonthlyHijriDatesResponse>
}