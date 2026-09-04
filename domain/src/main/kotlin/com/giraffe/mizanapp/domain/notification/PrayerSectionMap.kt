package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import java.time.Instant

fun prayerInstantFor(sectionId: String, times: PrayerTimes): Instant? = when (sectionId) { "fajr" -> times.fajr; "dhuhr" -> times.dhuhr; "asr" -> times.asr; "maghrib" -> times.maghrib; "isha" -> times.isha; else -> null }
fun nextPrayerAfter(sectionId: String, times: PrayerTimes): Instant? = when (sectionId) { "fajr" -> times.dhuhr; "dhuhr" -> times.asr; "asr" -> times.maghrib; "maghrib" -> times.isha; else -> null }
