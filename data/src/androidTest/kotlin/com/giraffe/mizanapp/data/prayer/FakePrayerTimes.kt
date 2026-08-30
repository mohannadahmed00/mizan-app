package com.giraffe.mizanapp.data.prayer

import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Test double for [PrayerTimesProvider]. Returns a literal outcome per date, or (if set) a
 * Maghrib at a fixed local time on every date not given a literal, or else
 * [PrayerTimesOutcome.NoLocation] — the fallback rather than a crash. Duplicated in every
 * consuming source set (see contracts/prayer-times-provider.md); keep the copies behaviourally
 * identical.
 */
class FakePrayerTimes : PrayerTimesProvider {
    private val outcomesByDate = mutableMapOf<LocalDate, PrayerTimesOutcome>()
    private var defaultMaghribLocalTime: LocalTime? = null
    var default: PrayerTimesOutcome = PrayerTimesOutcome.NoLocation

    fun setMaghrib(date: LocalDate, maghrib: Instant) {
        outcomesByDate[date] = calculatedAt(date, maghrib)
    }

    fun setOutcome(date: LocalDate, outcome: PrayerTimesOutcome) {
        outcomesByDate[date] = outcome
    }

    /** Every date not given a literal outcome gets a Maghrib at this local time, every day. */
    fun setDefaultMaghribLocalTime(time: LocalTime) {
        defaultMaghribLocalTime = time
    }

    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome {
        outcomesByDate[date]?.let { return it }
        defaultMaghribLocalTime?.let { time ->
            return calculatedAt(date, date.atTime(time).atZone(zone).toInstant())
        }
        return default
    }

    private fun calculatedAt(date: LocalDate, maghrib: Instant) = PrayerTimesOutcome.Calculated(
        PrayerTimes(
            date = date,
            fajr = maghrib,
            dhuhr = maghrib,
            asr = maghrib,
            maghrib = maghrib,
            isha = maghrib,
        ),
    )
}
