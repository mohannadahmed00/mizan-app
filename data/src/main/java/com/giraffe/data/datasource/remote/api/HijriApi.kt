package com.giraffe.data.datasource.remote.api

import com.giraffe.data.datasource.remote.response.MonthlyHijriDatesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface HijriApi {
    @GET("gToHCalendar/{month}/{year}?calendarMethod=UAQ")
    suspend fun fetchHijriDatesForMonth(
        @Path("month") month: Int,
        @Path("year") year: Int,
    ): Response<MonthlyHijriDatesResponse>
}