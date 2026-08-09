package com.giraffe.mizanapp.domain.time

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField

/**
 * Converts a civil date to its Hijri label.
 *
 * Computed locally — no network, no I/O, no clock. The label is therefore
 * always available, which is why a day plan can carry one from the moment it is
 * created and never needs filling in later (FR-009a).
 *
 * The Hijri date is a **label attached to a day**, never the thing that defines
 * the day's boundaries (constitution Principle VII). No score, streak or
 * boundary depends on it, which is why the Umm al-Qura calendar implemented
 * here is adequate even though a particular local observational authority may
 * differ by a day.
 */
object HijriLabel {

    /**
     * Month names are supplied here rather than left to a formatter.
     *
     * `DateTimeFormatter`'s `MMMM` pattern falls back to the month *number* for
     * the Hijrah chronology on Android, which produced labels like "26 2 1448".
     * These are calendar nomenclature, not task content, so they are not part
     * of the catalogue.
     */
    private val monthNames = arrayOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )

    fun forDate(date: LocalDate): String {
        val hijri = HijrahChronology.INSTANCE.date(date)
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        val month = hijri.get(ChronoField.MONTH_OF_YEAR)
        val year = hijri.get(ChronoField.YEAR)

        return "$day ${monthNames[month - 1]} $year AH"
    }
}
