package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrayerSectionMapTest {
 private val times = PrayerTimes(LocalDate.of(2026, 9, 4), Instant.parse("2026-09-04T03:00:00Z"), Instant.parse("2026-09-04T10:00:00Z"), Instant.parse("2026-09-04T13:00:00Z"), Instant.parse("2026-09-04T16:00:00Z"), Instant.parse("2026-09-04T18:00:00Z"))
 @Test fun `only the five prayer sections map`() { assertEquals(times.fajr, prayerInstantFor("fajr", times)); assertEquals(times.dhuhr, prayerInstantFor("dhuhr", times)); assertEquals(times.asr, prayerInstantFor("asr", times)); assertEquals(times.maghrib, prayerInstantFor("maghrib", times)); assertEquals(times.isha, prayerInstantFor("isha", times)); assertEquals(times.maghrib, nextPrayerAfter("asr", times)); listOf("qiyam-witr", "quran", "adhkar", "fasting", "friday", "unknown").forEach { assertNull(prayerInstantFor(it, times)) } }
}
