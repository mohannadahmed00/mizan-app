package com.giraffe.mizanapp.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Device-local boundary state; deliberately not synchronisable. */
@Entity(tableName = "boundary_state")
data class BoundaryStateEntity(
    @PrimaryKey val id: Int = 0,
    val latitude: Double?, val longitude: Double?, val zoneIdWhenObtained: String?, val obtainedAt: Long?,
    val lastResolvedDate: String?, val lastResolvedRegime: String?,
    @ColumnInfo(defaultValue = "0") val promptShown: Boolean = false,
)
