package com.giraffe.mizanapp.data.mapper

import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanWithTasks
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.domain.catalogue.ScheduleRule
import com.giraffe.mizanapp.domain.catalogue.TaskVersion
import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlannedTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Entity to domain and back.
 *
 * Not exempt from test-first: a mapper that silently drops a field is exactly
 * the kind of bug that corrupts a recorded day without anything failing.
 */

// --- schedule rule ------------------------------------------------------------

private const val TYPE_EVERY_DAY = "everyDay"
private const val TYPE_DAYS_OF_WEEK = "daysOfWeek"

fun ScheduleRule.toTypeString(): String = when (this) {
    is ScheduleRule.EveryDay -> TYPE_EVERY_DAY
    is ScheduleRule.DaysOfWeek -> TYPE_DAYS_OF_WEEK
}

fun ScheduleRule.toDaysString(): String? = when (this) {
    is ScheduleRule.EveryDay -> null
    is ScheduleRule.DaysOfWeek -> days.joinToString(",") { it.name }
}

fun scheduleRuleFrom(type: String, days: String?): ScheduleRule = when (type) {
    TYPE_EVERY_DAY -> ScheduleRule.EveryDay
    TYPE_DAYS_OF_WEEK -> ScheduleRule.DaysOfWeek(
        days.orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .map { DayOfWeek.valueOf(it) }
            .toSet(),
    )
    // Not an expected state: it means storage is corrupt or a migration was missed.
    else -> error("unknown schedule rule discriminator: $type")
}

// --- task version -------------------------------------------------------------

fun TaskVersion.toEntity(id: String, updatedAt: Long): TaskVersionEntity = TaskVersionEntity(
    id = id,
    taskSlug = taskSlug,
    catalogueVersion = catalogueVersion,
    points = points,
    maxOccurrencesPerDay = maxOccurrencesPerDay,
    scheduleType = scheduleRule.toTypeString(),
    scheduleDays = scheduleRule.toDaysString(),
    updatedAt = updatedAt,
)

fun TaskVersionEntity.toDomain(): TaskVersion = TaskVersion(
    taskSlug = taskSlug,
    catalogueVersion = catalogueVersion,
    points = points,
    maxOccurrencesPerDay = maxOccurrencesPerDay,
    scheduleRule = scheduleRuleFrom(scheduleType, scheduleDays),
)

// --- day plan -----------------------------------------------------------------

fun DayPlan.toEntity(updatedAt: Long): DayPlanEntity = DayPlanEntity(
    id = id,
    date = date.toString(),
    catalogueVersion = catalogueVersion,
    hijriLabel = hijriLabel,
    availablePoints = availablePoints,
    updatedAt = updatedAt,
)

fun PlannedTask.toEntity(updatedAt: Long): PlannedTaskEntity = PlannedTaskEntity(
    id = id,
    dayPlanId = dayPlanId,
    taskSlug = taskSlug,
    sectionId = sectionId,
    sectionLabel = sectionLabel,
    sectionOrder = sectionOrder,
    displayPosition = displayPosition,
    label = label,
    points = points,
    maxOccurrencesPerDay = maxOccurrencesPerDay,
    updatedAt = updatedAt,
)

fun PlannedTaskEntity.toDomain(): PlannedTask = PlannedTask(
    id = id,
    dayPlanId = dayPlanId,
    taskSlug = taskSlug,
    sectionId = sectionId,
    sectionLabel = sectionLabel,
    sectionOrder = sectionOrder,
    displayPosition = displayPosition,
    label = label,
    points = points,
    maxOccurrencesPerDay = maxOccurrencesPerDay,
)

fun DayPlanWithTasks.toDomain(): DayPlan = DayPlan(
    id = plan.id,
    date = LocalDate.parse(plan.date),
    catalogueVersion = plan.catalogueVersion,
    hijriLabel = plan.hijriLabel,
    availablePoints = plan.availablePoints,
    plannedTasks = tasks.map { it.toDomain() },
)

// --- completion ---------------------------------------------------------------

fun Completion.toEntity(updatedAt: Long): CompletionEntity = CompletionEntity(
    id = id,
    dayPlanId = dayPlanId,
    taskSlug = taskSlug,
    creditedDate = creditedDate.toString(),
    pointsAwarded = pointsAwarded,
    recordedAt = recordedAt.toEpochMilli(),
    reversedAt = reversedAt?.toEpochMilli(),
    updatedAt = updatedAt,
)

fun CompletionEntity.toDomain(): Completion = Completion(
    id = id,
    dayPlanId = dayPlanId,
    taskSlug = taskSlug,
    creditedDate = LocalDate.parse(creditedDate),
    pointsAwarded = pointsAwarded,
    recordedAt = Instant.ofEpochMilli(recordedAt),
    reversedAt = reversedAt?.let { Instant.ofEpochMilli(it) },
)
