package com.giraffe.mizanapp.domain.time

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    fun forDate(date: LocalDate): String =
        HijrahChronology.INSTANCE.date(date).format(formatter)
}
