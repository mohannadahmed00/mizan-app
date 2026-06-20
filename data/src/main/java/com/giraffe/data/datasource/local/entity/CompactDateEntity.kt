package com.giraffe.data.datasource.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compact_dates")
data class CompactDateEntity(
    @PrimaryKey val gregorianDateKey: String,
    @Embedded(prefix = "hijri_") val hijri: SimpleDateEntity,
    @Embedded(prefix = "gregorian_") val gregorian: SimpleDateEntity,
)
