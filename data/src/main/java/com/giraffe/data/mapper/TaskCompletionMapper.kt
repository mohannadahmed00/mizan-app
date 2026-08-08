package com.giraffe.data.mapper

import com.giraffe.data.datasource.local.dao.HijriDateDao
import com.giraffe.data.datasource.local.dao.TaskDao
import com.giraffe.data.datasource.local.entity.TaskCompletionEntity
import com.giraffe.domain.model.TaskCompletion

suspend fun TaskCompletionEntity.toDomain(
    taskDao: TaskDao,
    hijriDateDao: HijriDateDao,
): TaskCompletion {
    val task = taskDao.getById(taskId)
    val gregorianDate = task?.let {
        val parts = gregorianDateKey.split("-")
        com.giraffe.domain.model.SimpleDate(
            day = parts[2].toIntOrNull() ?: 0,
            month = parts[1].toIntOrNull() ?: 0,
            year = parts[0].toIntOrNull() ?: 0
        )
    } ?: com.giraffe.domain.model.SimpleDate(0, 0, 0)
    val compactDate = hijriDateDao.getByGregorianDate(gregorianDateKey)?.toModel()
        ?: com.giraffe.domain.model.CompactDate(
            hijri = gregorianDate,
            gregorian = gregorianDate
        )
    return TaskCompletion(
        taskId = taskId,
        date = compactDate,
        completedAt = completedAt
    )
}

fun TaskCompletion.toEntity(): TaskCompletionEntity = TaskCompletionEntity(
    taskId = taskId,
    gregorianDateKey = "%04d-%02d-%02d".format(
        date.gregorian.year, date.gregorian.month, date.gregorian.day
    ),
    completedAt = completedAt
)
