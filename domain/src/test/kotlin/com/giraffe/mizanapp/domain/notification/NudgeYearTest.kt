package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.streak.ActivityDay
import com.giraffe.mizanapp.domain.streak.ActivityState
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-007: over a full simulated year at a fixed mid-latitude location, spanning both solstices,
 * every prayer nudge must land strictly inside its own window, and no day may produce more than
 * five prayer anchors.
 *
 * Prayer instants here are a synthetic seasonal approximation, not a real astronomical
 * calculation (that lives in `:data`'s Adhan-backed provider) — this test exercises the domain
 * decision functions' window arithmetic across a realistic range of day lengths, not the
 * calculation itself.
 */
class NudgeYearTest {

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")

    /** Strictly increasing fajr < dhuhr < asr < maghrib < isha on every day of the year, with
     *  fajr and maghrib swinging with the seasons the way a mid-latitude location's genuinely do. */
    private fun prayerTimesFor(date: LocalDate): PrayerTimes {
        val angle = 2 * Math.PI * (date.dayOfYear - 172) / 365.0
        val fajrMinutes = (4 * 60 + 30) - (60 * cos(angle)).toInt()
        val maghribMinutes = (18 * 60 + 30) + (90 * cos(angle)).toInt()
        fun at(minutesFromMidnight: Int) = date.atStartOfDay(zone).plusMinutes(minutesFromMidnight.toLong()).toInstant()
        return PrayerTimes(
            date = date,
            fajr = at(fajrMinutes),
            dhuhr = at(12 * 60 + 15),
            asr = at(15 * 60 + 45),
            maghrib = at(maghribMinutes),
            isha = at(maghribMinutes + 75),
        )
    }

    private fun task(sectionId: String, order: Int) = PlannedTask(
        id = "$sectionId-id", dayPlanId = "plan", taskSlug = "$sectionId-1", sectionId = sectionId, sectionLabel = sectionId,
        sectionOrder = order, displayPosition = 1, label = sectionId, points = 1, maxOccurrencesPerDay = 1,
    )

    private fun planFor(date: LocalDate) = DayPlan(
        id = "plan", date = date, catalogueVersion = 1, hijriLabel = "H", availablePoints = 5,
        plannedTasks = listOf(task("fajr", 1), task("dhuhr", 2), task("asr", 3), task("maghrib", 4), task("isha", 5)),
        origin = PlanOrigin.OPENED,
    )

    private fun boundaryFor(date: LocalDate, dayEndsAt: Instant) = BoundaryState(
        regime = BoundaryRegime.Maghrib,
        coordinates = Coordinates(30.0, 31.0),
        zoneIdWhenObtained = zone.id,
        resolvedDate = date,
        expiresAt = dayEndsAt,
        lastResolvedDate = date,
        lastResolvedRegime = BoundaryRegime.Maghrib,
    )

    private fun streak() = StreakSummary(
        current = 0, longest = 0, lastActiveDate = null, todayCounted = false,
        recentActivity = List(7) { ActivityDay(LocalDate.of(2026, 1, 1).minusDays(it.toLong()), ActivityState.NOT_RECORDED) },
        showBreakNotice = false, isAtRisk = false,
    )

    @Test fun everyNudgeLandsInsideItsOwnWindowAcrossAFullYear() {
        var date = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 12, 31)
        var checkedSolstices = 0
        while (!date.isAfter(end)) {
            val times = prayerTimesFor(date)
            val dayEndsAt = date.plusDays(1).atStartOfDay(zone).toInstant()
            val now = date.atStartOfDay(zone).plusHours(1).toInstant() // well before fajr
            val plan = boundaryFor(date, dayEndsAt)
            val result = buildNotificationPlan(
                now, zone, plan, times, planFor(date), emptyList(), streak(),
                NotificationPreferences(setOf(NotificationCategory.PRAYER_WINDOW), false, null),
                weekClosesAt = null, ledger = emptyList(),
            )
            val prayerAnchors = result.anchors.filter { it.category == NotificationCategory.PRAYER_WINDOW }

            assertTrue("day $date produced more than five prayer anchors: ${prayerAnchors.size}", prayerAnchors.size <= 5)

            prayerAnchors.forEach { anchor ->
                val subject = anchor.speaksFor as AnchorSubject.PrayerWindow
                val prayerInstant = prayerInstantFor(subject.sectionId, times)
                assertTrue("$date ${subject.sectionId}: fires at ${anchor.firesAt}, must be strictly after $prayerInstant", anchor.firesAt.isAfter(prayerInstant))
                assertTrue("$date ${subject.sectionId}: fires at ${anchor.firesAt}, must be strictly before window end ${subject.windowEndsAt}", anchor.firesAt.isBefore(subject.windowEndsAt))
            }

            if (date.month == java.time.Month.JUNE && date.dayOfMonth == 21) checkedSolstices++
            if (date.month == java.time.Month.DECEMBER && date.dayOfMonth == 21) checkedSolstices++
            date = date.plusDays(1)
        }
        assertTrue("both solstices must have been exercised", checkedSolstices == 2)
    }
}
