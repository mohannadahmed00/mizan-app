package com.giraffe.mizanapp.domain.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single prayer-times provider required by Constitution Principle VII.
 * Spec 010 consumes this provider rather than adding a second.
 */
interface PrayerTimesProvider {
    suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome
}

data class PrayerTimes(
    val date: LocalDate,
    val fajr: Instant,
    val dhuhr: Instant,
    val asr: Instant,
    val maghrib: Instant,
    val isha: Instant,
)

sealed interface PrayerTimesOutcome {
    data class Calculated(val times: PrayerTimes) : PrayerTimesOutcome
    data object NoLocation : PrayerTimesOutcome
    data class CalculationFailed(val reason: String) : PrayerTimesOutcome
}
