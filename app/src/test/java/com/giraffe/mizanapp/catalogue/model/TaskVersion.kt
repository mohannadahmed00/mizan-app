package com.giraffe.mizanapp.catalogue.model

import kotlinx.serialization.Serializable

/**
 * What a task was worth under a given catalogue version.
 *
 * This is the thing a past day is scored against. Points live here rather than
 * on [TaskDefinition] so that changing a task's value creates a new version
 * instead of rewriting history.
 */
@Serializable
data class TaskVersion(
    val taskSlug: String,
    val catalogueVersion: Int,
    val points: Int,
    val maxOccurrencesPerDay: Int,
    val scheduleRule: ScheduleRule,
) {
    /** Points this task contributes to a day it applies to. */
    val availablePoints: Int get() = points * maxOccurrencesPerDay
}
