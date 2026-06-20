package com.giraffe.data.datasource.remote.response

import com.giraffe.data.datasource.remote.dto.CompactDateDto

data class MonthlyHijriDatesResponse(
    val code: Int,
    val data: List<CompactDateDto>,
    val status: String
)