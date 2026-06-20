package com.giraffe.domain.repository

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.Task

interface Repository {
    suspend fun getCurrentDate(): CompactDate
    suspend fun getTodayTasks(): List<Task>
    suspend fun syncMonthlyHijriDates()
}