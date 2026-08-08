package com.giraffe.data.repository

import com.giraffe.data.datasource.local.dao.HijriDateDao
import com.giraffe.data.datasource.local.dao.TaskCompletionDao
import com.giraffe.data.datasource.local.dao.TaskDao
import com.giraffe.data.datasource.local.entity.TaskCompletionEntity
import com.giraffe.data.datasource.remote.api.HijriApi
import com.giraffe.data.datasource.remote.dto.CompactDateDto
import com.giraffe.data.mapper.toDomain
import com.giraffe.data.mapper.toEntity
import com.giraffe.data.mapper.toModel
import com.giraffe.data.utli.executeApiSafely
import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate
import com.giraffe.domain.model.Task
import com.giraffe.domain.model.TaskCompletion
import com.giraffe.domain.repository.HijriDateRepository
import org.koin.core.annotation.Single

@Single
class HijriDateRepositoryImpl(
    private val hijriApi: HijriApi,
    private val hijriDateDao: HijriDateDao,
    private val taskDao: TaskDao,
    private val taskCompletionDao: TaskCompletionDao,
) : HijriDateRepository {
    override suspend fun getCompactDateOf(date: SimpleDate): CompactDate? {
        val key = "%02d-%02d-%04d".format(date.day, date.month, date.year)
        return hijriDateDao.getByGregorianDate(key)?.toModel()
    }

    override suspend fun getTodayTasks(): List<Task> {
        return taskDao.getAllActive().map { it.toDomain() }
    }

    override suspend fun syncMonthlyHijriDates(month: Int, year: Int) {
        executeApiSafely {
            hijriApi.fetchHijriDatesForMonth(month = month, year = year)
        }.also { response ->
            val dates = response.data.map(CompactDateDto::toEntity)
            hijriDateDao.insertAll(dates)
        }
    }

    override suspend fun getCompletionsForDate(dateKey: String): List<TaskCompletion> {
        return taskCompletionDao.getCompletionsForDate(dateKey).map { entity ->
            entity.toDomain(taskDao, hijriDateDao)
        }
    }

    override suspend fun getAllCompletions(): List<TaskCompletion> {
        return taskCompletionDao.getAllCompletions().map { entity ->
            entity.toDomain(taskDao, hijriDateDao)
        }
    }

    override suspend fun insertCompletion(taskId: Long, dateKey: String, completedAt: Long) {
        taskCompletionDao.insert(
            TaskCompletionEntity(
                taskId = taskId,
                gregorianDateKey = dateKey,
                completedAt = completedAt
            )
        )
    }

    override suspend fun deleteCompletion(taskId: Long, dateKey: String) {
        taskCompletionDao.delete(taskId, dateKey)
    }
}
