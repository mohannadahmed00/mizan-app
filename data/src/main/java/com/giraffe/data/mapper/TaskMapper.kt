package com.giraffe.data.mapper

import com.giraffe.data.datasource.local.entity.TaskEntity
import com.giraffe.domain.model.Category
import com.giraffe.domain.model.Day
import com.giraffe.domain.model.Task

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    name = name,
    category = Category.valueOf(category),
    points = points,
    activeDays = activeDays.split(",").map { Day.valueOf(it) }.toSet()
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    name = name,
    category = category.name,
    points = points,
    activeDays = activeDays.joinToString(",") { it.name },
    isActive = true
)
