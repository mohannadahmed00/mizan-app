package com.giraffe.mizanapp.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_preferences")
data class NotificationPreferencesEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(defaultValue = "0") val prayerWindowEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val streakAtRiskEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val weeklySummaryEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val allSilenced: Boolean = false,
    val quietStart: String? = null, val quietEnd: String? = null, val permissionAskedAt: Long? = null,
)
