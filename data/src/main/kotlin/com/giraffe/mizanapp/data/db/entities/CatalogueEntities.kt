package com.giraffe.mizanapp.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Administrator content. Replaced wholesale by catalogue version rather than
 * merged, so these carry no sync bookkeeping — they are not user records.
 */
@Entity(tableName = "sections")
data class SectionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val displayOrder: Int,
)

@Entity(tableName = "task_definitions")
data class TaskDefinitionEntity(
    @PrimaryKey val slug: String,
    val sectionId: String,
    val displayPosition: Int,
    val label: String,
)

@Entity(tableName = "catalogue_versions")
data class CatalogueVersionEntity(
    @PrimaryKey val version: Int,
    val effectiveFrom: String,
)

/**
 * What a task was worth under one catalogue version.
 *
 * The schedule rule is stored as a discriminator plus a comma-separated day
 * list: a sealed type does not map to a column, and a reserved `dateAnchored`
 * variant must be addable later without a destructive migration.
 *
 * Synchronisable, so it carries the Principle V columns.
 */
@Entity(
    tableName = "task_versions",
    indices = [Index(value = ["taskSlug", "catalogueVersion"], unique = true)],
)
data class TaskVersionEntity(
    @PrimaryKey val id: String,
    val taskSlug: String,
    val catalogueVersion: Int,
    val points: Int,
    val maxOccurrencesPerDay: Int,
    val scheduleType: String,
    val scheduleDays: String?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val userId: String? = null,
)
