package com.giraffe.data.datasource.remote.dto

data class CompactDateDto(
    val gregorian: Gregorian,
    val hijri: Hijri
) {
    data class Gregorian(
        val date: String,
        val day: String,
        val designation: Designation,
        val format: String,
        val lunarSighting: Boolean,
        val month: Month,
        val weekday: Weekday,
        val year: String
    ) {
        data class Designation(
            val abbreviated: String,
            val expanded: String
        )

        data class Month(
            val en: String,
            val number: Int
        )

        data class Weekday(
            val en: String
        )
    }

    data class Hijri(
        val adjustedHolidays: List<Any>,
        val date: String,
        val day: String,
        val designation: Designation,
        val format: String,
        val holidays: List<String>,
        val method: String,
        val month: Month,
        val weekday: Weekday,
        val year: String
    ) {
        data class Designation(
            val abbreviated: String,
            val expanded: String
        )

        data class Month(
            val ar: String,
            val days: Int,
            val en: String,
            val number: Int
        )

        data class Weekday(
            val ar: String,
            val en: String
        )
    }
}