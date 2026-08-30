package com.giraffe.mizanapp.data.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates as AdhanCoordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes as AdhanPrayerTimesResult
import com.batoulapps.adhan.data.DateComponents
import com.giraffe.mizanapp.domain.prayer.AsrMadhab
import com.giraffe.mizanapp.domain.prayer.CalculationConvention
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.prayer.RegionConventionMapping
import com.giraffe.mizanapp.domain.prayer.conventionFor
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single file in the app allowed to import `com.batoulapps.adhan` (the Java port; `adhan2`
 * was rejected — see research R5). Computes entirely on-device, selects its convention from the
 * region rather than a user setting, and never substitutes a guessed location.
 */
class AdhanPrayerTimes(
    private val mapping: RegionConventionMapping,
) : PrayerTimesProvider {

    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome {
        val selected = conventionFor(zone.id, mapping)
        val parameters = selected.convention.toCalculationMethod().parameters.apply {
            madhab = selected.asr.toMadhab()
        }
        val components = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        val coordinates = AdhanCoordinates(at.latitude, at.longitude)
        val result = try {
            AdhanPrayerTimesResult(coordinates, components, parameters)
        } catch (e: Exception) {
            return PrayerTimesOutcome.CalculationFailed(e.message ?: "adhan calculation failed")
        }
        return PrayerTimesOutcome.Calculated(
            PrayerTimes(
                date = date,
                fajr = result.fajr.toInstant(),
                dhuhr = result.dhuhr.toInstant(),
                asr = result.asr.toInstant(),
                maghrib = result.maghrib.toInstant(),
                isha = result.isha.toInstant(),
            ),
        )
    }

    private fun CalculationConvention.toCalculationMethod(): CalculationMethod = when (this) {
        CalculationConvention.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        CalculationConvention.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        CalculationConvention.EGYPTIAN -> CalculationMethod.EGYPTIAN
        CalculationConvention.ISNA -> CalculationMethod.NORTH_AMERICA
        CalculationConvention.KARACHI -> CalculationMethod.KARACHI
    }

    private fun AsrMadhab.toMadhab(): Madhab = when (this) {
        AsrMadhab.STANDARD -> Madhab.SHAFI
        AsrMadhab.HANAFI -> Madhab.HANAFI
    }

    private val CalculationMethod.parameters get() = getParameters()
}
