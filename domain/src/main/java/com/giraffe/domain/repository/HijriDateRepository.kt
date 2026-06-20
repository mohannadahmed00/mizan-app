package com.giraffe.domain.repository

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.Task

interface HijriDateRepository {
    suspend fun getCompactDateOf(date: SimpleDate): CompactDate?
    suspend fun getTodayTasks(): List<Task>
    suspend fun syncMonthlyHijriDates(month: Int, year: Int)
}