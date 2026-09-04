package com.giraffe.mizanapp.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_deliveries")
data class NotificationDeliveryEntity(@PrimaryKey val anchorKey: String, val category: String, val state: String, val reason: String?, val decidedAt: Long, val heldUntil: Long?)
