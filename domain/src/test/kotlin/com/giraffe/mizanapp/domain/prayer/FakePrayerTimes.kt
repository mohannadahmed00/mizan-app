package com.giraffe.mizanapp.domain.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Test double for [PrayerTimesProvider]. Returns a literal outcome per date, defaulting to
 * [PrayerTimesOutcome.NoLocation] when nothing was set — the fallback rather than a crash.
 * Duplicated in every consuming source set (see contracts/prayer-times-provider.md); keep the
 * copies behaviourally identical.
 */
class FakePrayerTimes : PrayerTimesProvider {
    private val outcomesByDate = mutableMapOf<LocalDate, PrayerTimesOutcome>()
    var default: PrayerTimesOutcome = PrayerTimesOutcome.NoLocation

    fun setMaghrib(date: LocalDate, maghrib: Instant) {
        outcomesByDate[date] = PrayerTimesOutcome.Calculated(
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

    fun setOutcome(date: LocalDate, outcome: PrayerTimesOutcome) {
        outcomesByDate[date] = outcome
    }

    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome =
        outcomesByDate[date] ?: default
}
