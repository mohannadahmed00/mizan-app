package com.giraffe.domain.repository

import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.Task
import com.giraffe.domain.model.TaskCompletion

interface HijriDateRepository {
    suspend fun getCompactDateOf(date: SimpleDate): CompactDate?
    suspend fun getTodayTasks(): List<Task>
    suspend fun syncMonthlyHijriDates(month: Int, year: Int)
    suspend fun getCompletionsForDate(dateKey: String): List<TaskCompletion>
    suspend fun getAllCompletions(): List<TaskCompletion>
    suspend fun insertCompletion(taskId: Long, dateKey: String, completedAt: Long)
    suspend fun deleteCompletion(taskId: Long, dateKey: String)
}
