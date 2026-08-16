package com.giraffe.mizanapp.data.db.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * The frozen record of a date.
 *
 * Written once, never updated — there is no DAO method that could update one.
 * Synchronisable, so it carries a client-generated id, a last-modified stamp, a
 * tombstone marker and a nullable user reference (Principle V).
 */
@Entity(
    tableName = "day_plans",
    indices = [Index(value = ["date"], unique = true)],
)
data class DayPlanEntity(
    @PrimaryKey val id: String,
    val date: String,
    val catalogueVersion: Int,
    val hijriLabel: String,
    val availablePoints: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val userId: String? = null,
    val origin: String = "OPENED",
    val syncedAt: Long? = null,
)

@Entity(
    tableName = "planned_tasks",
    indices = [
        Index(value = ["dayPlanId"]),
        Index(value = ["dayPlanId", "taskSlug"], unique = true),
    ],
)
data class PlannedTaskEntity(
    @PrimaryKey val id: String,
    val dayPlanId: String,
    val taskSlug: String,
    val sectionId: String,
    val sectionLabel: String,
    val sectionOrder: Int,
    val displayPosition: Int,
    val label: String,
    val points: Int,
    val maxOccurrencesPerDay: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val userId: String? = null,
)

/**
 * One recorded occurrence.
 *
 * [reversedAt] is the undo tombstone. Every read filters on it being null —
 * a reversed row consumes no occurrence slot and contributes no points.
 */
@Entity(
    tableName = "completions",
    indices = [
        Index(value = ["creditedDate"]),
        Index(value = ["dayPlanId", "taskSlug"]),
        Index(value = ["creditedDate", "taskSlug", "reversedAt"]),
    ],
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    val dayPlanId: String,
    val taskSlug: String,
    val creditedDate: String,
    val pointsAwarded: Int,
    val recordedAt: Long,
    val reversedAt: Long? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val userId: String? = null,
    val syncedAt: Long? = null,
)

/** A day plan with its planned tasks, read in one query. */
data class DayPlanWithTasks(
    @Embedded val plan: DayPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "dayPlanId")
    val tasks: List<PlannedTaskEntity>,
)
