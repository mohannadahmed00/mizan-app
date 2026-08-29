package com.giraffe.mizanapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The current account's disposable consent snapshot is isolated from recorded
 * history so signing out can remove it without touching personal records.
 */
@Entity(tableName = "participation_state")
data class ParticipationStateEntity(
    @PrimaryKey val id: Int = 1,
    val optedIn: Boolean = false,
    val regionId: String?,
    val regionDisplayName: String?,
    val updatedAt: Long,
)
