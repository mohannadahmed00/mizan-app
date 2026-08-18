package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import com.giraffe.mizanapp.domain.catalogue.CatalogueVersion
import com.giraffe.mizanapp.domain.catalogue.ScheduleRule
import com.giraffe.mizanapp.domain.catalogue.Section
import com.giraffe.mizanapp.domain.catalogue.TaskDefinition
import com.giraffe.mizanapp.domain.catalogue.TaskVersion
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.json.Json

private val fixtureJson = Json { classDiscriminator = "type" }

/**
 * A second catalogue version, valid under `CatalogueValidator`'s fixed
 * per-weekday and per-section totals but disjoint from the seeded version 1
 * in per-task points, in one task's occurrence limit, and in one task's
 * identity (`friday-8` replaces `friday-7`) — everything the Principle III
 * test needs to prove a newer catalogue never re-derives a day already
 * recorded under an older one.
 */
fun catalogueVersion2(effectiveFrom: LocalDate): Catalogue {
    val sections = listOf(
        Section("fajr", "Fajr", 1),
        Section("dhuhr", "Dhuhr", 2),
        Section("asr", "Asr", 3),
        Section("maghrib", "Maghrib", 4),
        Section("isha", "Isha", 5),
        Section("qiyam-witr", "Qiyam Witr", 6),
        Section("quran", "Quran", 7),
        Section("adhkar", "Adhkar", 8),
        Section("fasting", "Fasting", 9),
        Section("friday", "Friday", 10),
    )

    val tasks = listOf(
        TaskDefinition("fajr-1", "fajr", 1, "Fajr 1"),
        TaskDefinition("fajr-2", "fajr", 2, "Fajr 2"),
        TaskDefinition("fajr-3", "fajr", 3, "Fajr 3"),
        TaskDefinition("fajr-4", "fajr", 4, "Fajr 4"),
        TaskDefinition("fajr-5", "fajr", 5, "Fajr 5"),
        TaskDefinition("fajr-6", "fajr", 6, "Fajr 6"),
        TaskDefinition("dhuhr-1", "dhuhr", 1, "Dhuhr 1"),
        TaskDefinition("dhuhr-2", "dhuhr", 2, "Dhuhr 2"),
        TaskDefinition("dhuhr-3", "dhuhr", 3, "Dhuhr 3"),
        TaskDefinition("dhuhr-4", "dhuhr", 4, "Dhuhr 4"),
        TaskDefinition("asr-1", "asr", 1, "Asr 1"),
        TaskDefinition("asr-2", "asr", 2, "Asr 2"),
        TaskDefinition("asr-3", "asr", 3, "Asr 3"),
        TaskDefinition("maghrib-1", "maghrib", 1, "Maghrib 1"),
        TaskDefinition("maghrib-2", "maghrib", 2, "Maghrib 2"),
        TaskDefinition("maghrib-3", "maghrib", 3, "Maghrib 3"),
        TaskDefinition("isha-1", "isha", 1, "Isha 1"),
        TaskDefinition("isha-2", "isha", 2, "Isha 2"),
        TaskDefinition("isha-3", "isha", 3, "Isha 3"),
        TaskDefinition("qiyam", "qiyam-witr", 1, "Qiyam"),
        TaskDefinition("witr", "qiyam-witr", 2, "Witr"),
        TaskDefinition("quran-memorisation", "quran", 1, "Quran memorisation"),
        TaskDefinition("quran-reading", "quran", 2, "Quran reading"),
        TaskDefinition("adhkar", "adhkar", 1, "Adhkar"),
        TaskDefinition("fast-voluntary", "fasting", 1, "Voluntary fast"),
        TaskDefinition("friday-1", "friday", 1, "Friday 1"),
        TaskDefinition("friday-2", "friday", 2, "Friday 2"),
        TaskDefinition("friday-3", "friday", 3, "Friday 3"),
        TaskDefinition("friday-4", "friday", 4, "Friday 4"),
        TaskDefinition("friday-5", "friday", 5, "Friday 5"),
        TaskDefinition("friday-6", "friday", 6, "Friday 6"),
        TaskDefinition("friday-8", "friday", 7, "Friday 8"),
    )

    fun everyDay(slug: String, points: Int, maxOccurrencesPerDay: Int = 1) =
        TaskVersion(slug, 2, points, maxOccurrencesPerDay, ScheduleRule.EveryDay)

    fun onFriday(slug: String, points: Int) =
        TaskVersion(slug, 2, points, 1, ScheduleRule.DaysOfWeek(setOf(DayOfWeek.FRIDAY)))

    val taskVersions = listOf(
        everyDay("fajr-1", 1), everyDay("fajr-2", 1), everyDay("fajr-3", 3),
        everyDay("fajr-4", 3), everyDay("fajr-5", 2), everyDay("fajr-6", 2),
        everyDay("dhuhr-1", 1), everyDay("dhuhr-2", 3), everyDay("dhuhr-3", 2), everyDay("dhuhr-4", 2),
        everyDay("asr-1", 1), everyDay("asr-2", 2), everyDay("asr-3", 3),
        everyDay("maghrib-1", 3), everyDay("maghrib-2", 1), everyDay("maghrib-3", 2),
        everyDay("isha-1", 2), everyDay("isha-2", 3), everyDay("isha-3", 1),
        everyDay("qiyam", 4), everyDay("witr", 5),
        everyDay("quran-memorisation", 1), everyDay("quran-reading", 3),
        everyDay("adhkar", 3, maxOccurrencesPerDay = 6),
        TaskVersion(
            "fast-voluntary", 2, 5, 1,
            ScheduleRule.DaysOfWeek(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
        ),
        onFriday("friday-1", 1), onFriday("friday-2", 1), onFriday("friday-3", 1),
        onFriday("friday-4", 1), onFriday("friday-5", 1), onFriday("friday-6", 1),
        onFriday("friday-8", 1),
    )

    return Catalogue(
        versions = listOf(CatalogueVersion(2, effectiveFrom)),
        sections = sections,
        tasks = tasks,
        taskVersions = taskVersions,
    )
}

fun catalogueVersion2Payload(effectiveFrom: LocalDate): String =
    fixtureJson.encodeToString(Catalogue.serializer(), catalogueVersion2(effectiveFrom))
