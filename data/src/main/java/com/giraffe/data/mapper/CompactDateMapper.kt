package com.giraffe.data.mapper

import com.giraffe.data.datasource.local.entity.CompactDateEntity
import com.giraffe.data.datasource.local.entity.SimpleDateEntity
import com.giraffe.data.datasource.remote.dto.CompactDateDto
import com.giraffe.domain.model.CompactDate
import com.giraffe.domain.model.SimpleDate

fun CompactDate.toEntity(): CompactDateEntity {
    val key = "%04d-%02d-%02d".format(gregorian.year, gregorian.month, gregorian.day)
    return CompactDateEntity(
        gregorianDateKey = key,
        hijri = SimpleDateEntity(hijri.day, hijri.month, hijri.year),
        gregorian = SimpleDateEntity(gregorian.day, gregorian.month, gregorian.year),
    )
}

fun CompactDateEntity.toModel(): CompactDate = CompactDate(
    hijri = SimpleDate(hijri.day, hijri.month, hijri.year),
    gregorian = SimpleDate(gregorian.day, gregorian.month, gregorian.year),
)

fun CompactDateDto.toEntity(): CompactDateEntity {
    val key = "%02d-%02d-%04d".format(
        gregorian.day.toIntOrNull(),
        gregorian.month.number,
        gregorian.year.toIntOrNull()
    )
    return CompactDateEntity(
        gregorianDateKey = key,
        hijri = SimpleDateEntity(
            day = hijri.day.toIntOrNull() ?: 0,
            month = hijri.month.number,
            year = hijri.year.toIntOrNull() ?: 0
        ),
        gregorian = SimpleDateEntity(
            day = gregorian.day.toIntOrNull() ?: 0,
            month = gregorian.month.number,
            year = gregorian.year.toIntOrNull() ?: 0
        ),
    )
}