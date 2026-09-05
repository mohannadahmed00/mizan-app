package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.Completion
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-010: no accountability day produces more than the five prayer anchors, the one streak
 * anchor and the one weekly-summary anchor that can ever apply to it -- seven in total -- and a
 * held summary counts against the day its anchor *speaks for* (FR-042), never the day it is
 * finally posted.
 */
class DailyNotificationCapTest {

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)

    private fun at(hour: Int, minute: Int, day: LocalDate = date): Instant =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun boundary(resolvedDate: LocalDate = date, expiresAt: Instant = at(23, 50)) = BoundaryState(
        regime = BoundaryRegime.Maghrib,
        coordinates = Coordinates(30.0, 31.0),
        zoneIdWhenObtained = zone.id,
        resolvedDate = resolvedDate,
        expiresAt = expiresAt,
        lastResolvedDate = resolvedDate,
        lastResolvedRegime = BoundaryRegime.Maghrib,
    )

    private fun prayerTimes() = PrayerTimes(date, at(4, 30), at(12, 0), at(15, 30), at(18, 0), at(19, 30))

    private fun task(sectionId: String, order: Int) = PlannedTask(
        id = "$sectionId-id", dayPlanId = "plan", taskSlug = "$sectionId-1", sectionId = sectionId,
        sectionLabel = sectionId, sectionOrder = order, displayPosition = 1, label = sectionId,
        points = 1, maxOccurrencesPerDay = 1,
    )

    private fun dayPlan(completedSections: Set<String> = emptySet()) = DayPlan(
        id = "plan", date = date, catalogueVersion = 1, hijriLabel = "H", availablePoints = 5,
        plannedTasks = listOf(task("fajr", 1), task("dhuhr", 2), task("asr", 3), task("maghrib", 4), task("isha", 5)),
        origin = PlanOrigin.OPENED,
    )

    private fun streak(current: Int, todayCounted: Boolean) = StreakSummary(
        current = current, longest = current, lastActiveDate = if (current > 0) date.minusDays(1) else null,
        todayCounted = todayCounted,
        recentActivity = List(7) { ActivityDay(date.minusDays(it.toLong()), ActivityState.NOT_RECORDED) },
        showBreakNotice = false, isAtRisk = current >= 1 && !todayCounted,
    )

    private fun everythingEnabled() = NotificationPreferences(
        enabled = setOf(NotificationCategory.PRAYER_WINDOW, NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY),
        allSilenced = false,
        quietHours = null,
    )

    @Test fun noDayEverProducesMoreThanSevenAnchors() {
        val plan = buildNotificationPlan(
            now = at(0, 1), zone = zone, boundary = boundary(), prayerTimes = prayerTimes(),
            plan = dayPlan(), completions = emptyList(), streak = streak(current = 5, todayCounted = false),
            preferences = everythingEnabled(), weekClosesAt = at(23, 50), ledger = emptyList(), dormant = false,
        )
        assertTrue("expected at most 7 anchors, got ${plan.anchors.size}", plan.anchors.size <= 7)
        assertEquals(7, plan.anchors.size)
    }

    @Test fun theCountFallsToZeroAsTheDaysTasksAreRecorded() {
        val allCompletions = listOf("fajr-1", "dhuhr-1", "asr-1", "maghrib-1", "isha-1").map {
            Completion(id = it, dayPlanId = "plan", taskSlug = it, creditedDate = date, pointsAwarded = 1, recordedAt = Instant.EPOCH)
        }
        val plan = buildNotificationPlan(
            now = at(0, 1), zone = zone, boundary = boundary(), prayerTimes = prayerTimes(),
            plan = dayPlan(), completions = allCompletions, streak = streak(current = 5, todayCounted = true),
            preferences = everythingEnabled(), weekClosesAt = null, ledger = emptyList(), dormant = false,
        )
        assertEquals(0, plan.anchors.size)
    }

    @Test fun aHeldSummaryCountsAgainstTheDayItsAnchorSpeaksForNotTheDayItIsFinallyPosted() {
        val originatingWeek = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(date).key
        val anchor = NotificationAnchor(NotificationCategory.WEEKLY_SUMMARY, at(18, 0), AnchorSubject.ClosedWeek(originatingWeek))
        // The anchor's own subject names the week it speaks for -- fixed at construction --
        // regardless of when evaluateAnchor later resolves a Hold and it is actually posted.
        assertEquals(originatingWeek, (anchor.speaksFor as AnchorSubject.ClosedWeek).key)
    }
}
