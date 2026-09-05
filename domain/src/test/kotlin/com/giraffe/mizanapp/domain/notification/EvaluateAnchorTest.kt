package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.streak.ActivityDay
import com.giraffe.mizanapp.domain.streak.ActivityState
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateAnchorTest {

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private val now: Instant = at(9, 0)
    private val dayEndsAt: Instant = at(23, 50)

    private fun at(hour: Int, minute: Int, day: LocalDate = date): Instant =
        LocalDateTime.of(day, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun boundary(resolvedDate: LocalDate = date) = BoundaryState(
        regime = BoundaryRegime.Maghrib,
        coordinates = com.giraffe.mizanapp.domain.prayer.Coordinates(30.0, 31.0),
        zoneIdWhenObtained = zone.id,
        resolvedDate = resolvedDate,
        expiresAt = dayEndsAt,
        lastResolvedDate = resolvedDate,
        lastResolvedRegime = BoundaryRegime.Maghrib,
    )

    private fun task(sectionId: String, slug: String = "$sectionId-1") = PlannedTask(
        id = "$slug-id", dayPlanId = "plan", taskSlug = slug, sectionId = sectionId, sectionLabel = sectionId,
        sectionOrder = 1, displayPosition = 1, label = sectionId, points = 1, maxOccurrencesPerDay = 1,
    )

    private fun dayPlan() = DayPlan(
        id = "plan", date = date, catalogueVersion = 1, hijriLabel = "H", availablePoints = 5,
        plannedTasks = listOf(task("asr")), origin = PlanOrigin.OPENED,
    )

    private fun streak(current: Int = 0, todayCounted: Boolean = false) = StreakSummary(
        current = current, longest = current, lastActiveDate = if (current > 0) date.minusDays(1) else null,
        todayCounted = todayCounted,
        recentActivity = List(7) { ActivityDay(date.minusDays(it.toLong()), ActivityState.NOT_RECORDED) },
        showBreakNotice = false, isAtRisk = current >= 1 && !todayCounted,
    )

    private fun preferences(
        enabled: Set<NotificationCategory> = setOf(NotificationCategory.PRAYER_WINDOW, NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY),
        allSilenced: Boolean = false,
        quietHours: QuietHours? = null,
    ) = NotificationPreferences(enabled, allSilenced, quietHours)

    private fun prayerAnchor(windowEndsAt: Instant = at(15, 30), sectionId: String = "asr", subjectDate: LocalDate = date) =
        NotificationAnchor(NotificationCategory.PRAYER_WINDOW, at(15, 10), AnchorSubject.PrayerWindow(subjectDate, sectionId, windowEndsAt))

    private fun streakAnchor(subjectDate: LocalDate = date) =
        NotificationAnchor(NotificationCategory.STREAK_AT_RISK, at(19, 0), AnchorSubject.Day(subjectDate))

    private fun summaryAnchor(key: WeekKey = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(date).key) =
        NotificationAnchor(NotificationCategory.WEEKLY_SUMMARY, at(18, 5), AnchorSubject.ClosedWeek(key))

    private fun evaluate(
        anchor: NotificationAnchor,
        now: Instant = this.now,
        boundary: BoundaryState = boundary(),
        dayPlan: DayPlan? = dayPlan(),
        completions: List<Completion> = emptyList(),
        streak: StreakSummary = streak(),
        preferences: NotificationPreferences = preferences(),
        ledger: DeliveryRecord? = null,
        hasPermission: Boolean = true,
        dormant: Boolean = false,
        summary: com.giraffe.mizanapp.domain.week.WeekSummary? = null,
        tasksRecorded: Int = 0,
    ) = evaluateAnchor(anchor, now, zone, boundary, dayPlan, completions, streak, preferences, summary, ledger, hasPermission, dormant, tasksRecorded)

    // --- shared order-of-checks cases ---

    @Test fun `terminal ledger produces ALREADY_DELIVERED`() {
        val ledger = DeliveryRecord("k", NotificationCategory.STREAK_AT_RISK, DeliveryState.DELIVERED, null, now, null)
        val result = evaluate(streakAnchor(), ledger = ledger)
        assertEquals(NotificationVerdict.Discard(DiscardReason.ALREADY_DELIVERED), result)
    }

    @Test fun `no permission produces NO_PERMISSION`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 2), hasPermission = false)
        assertEquals(NotificationVerdict.Discard(DiscardReason.NO_PERMISSION), result)
    }

    @Test fun `silenced produces ALL_SILENCED`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 2), preferences = preferences(allSilenced = true))
        assertEquals(NotificationVerdict.Discard(DiscardReason.ALL_SILENCED), result)
    }

    @Test fun `category off produces CATEGORY_OFF`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 2), preferences = preferences(enabled = setOf(NotificationCategory.WEEKLY_SUMMARY)))
        assertEquals(NotificationVerdict.Discard(DiscardReason.CATEGORY_OFF), result)
    }

    @Test fun `subject naming a different date than resolvedDate produces DAY_ROLLED_OVER`() {
        val result = evaluate(streakAnchor(subjectDate = date.minusDays(1)), streak = streak(current = 2))
        assertEquals(NotificationVerdict.Discard(DiscardReason.DAY_ROLLED_OVER), result)
    }

    @Test fun `ALREADY_DELIVERED wins over NO_PERMISSION when both apply`() {
        val ledger = DeliveryRecord("k", NotificationCategory.STREAK_AT_RISK, DeliveryState.DELIVERED, null, now, null)
        val result = evaluate(streakAnchor(), ledger = ledger, hasPermission = false)
        assertEquals(NotificationVerdict.Discard(DiscardReason.ALREADY_DELIVERED), result)
    }

    // --- prayer window cases ---

    @Test fun `now at or after windowEndsAt produces WINDOW_PASSED`() {
        val anchor = prayerAnchor(windowEndsAt = at(9, 0))
        val result = evaluate(anchor, now = at(9, 0))
        assertEquals(NotificationVerdict.Discard(DiscardReason.WINDOW_PASSED), result)
    }

    @Test fun `section complete at fire time produces SECTION_COMPLETE`() {
        val completions = listOf(Completion("c1", "plan", "asr-1", date, 1, at(8, 0)))
        val result = evaluate(prayerAnchor(), completions = completions)
        assertEquals(NotificationVerdict.Discard(DiscardReason.SECTION_COMPLETE), result)
    }

    @Test fun `prayer window inside quiet hours produces QUIET_HOURS not Hold`() {
        val quiet = QuietHours(LocalTime.of(8, 0), LocalTime.of(10, 0))
        val result = evaluate(prayerAnchor(), preferences = preferences(quietHours = quiet))
        assertEquals(NotificationVerdict.Discard(DiscardReason.QUIET_HOURS), result)
    }

    @Test fun `prayer window otherwise posts`() {
        val result = evaluate(prayerAnchor())
        assertTrue(result is NotificationVerdict.Post)
    }

    @Test fun `prayer window Post carries the section and what remains available in it`() {
        val result = evaluate(prayerAnchor()) as NotificationVerdict.Post
        assertEquals("asr", result.content.bodyArgs["section"])
        assertEquals("1", result.content.bodyArgs["remaining"])
    }

    // --- streak cases ---

    @Test fun `todayCounted at fire time produces DAY_ALREADY_COUNTED`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 3, todayCounted = true))
        assertEquals(NotificationVerdict.Discard(DiscardReason.DAY_ALREADY_COUNTED), result)
    }

    @Test fun `current zero produces NO_LIVE_STREAK`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 0))
        assertEquals(NotificationVerdict.Discard(DiscardReason.NO_LIVE_STREAK), result)
    }

    @Test fun `streak inside quiet hours produces QUIET_HOURS`() {
        val quiet = QuietHours(LocalTime.of(8, 0), LocalTime.of(10, 0))
        val result = evaluate(streakAnchor(), streak = streak(current = 3), preferences = preferences(quietHours = quiet))
        assertEquals(NotificationVerdict.Discard(DiscardReason.QUIET_HOURS), result)
    }

    @Test fun `streak otherwise posts`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 3))
        assertTrue(result is NotificationVerdict.Post)
    }

    // --- weekly summary cases ---

    @Test fun `weekly summary inside quiet hours returns Hold not Discard`() {
        val quiet = QuietHours(LocalTime.of(8, 0), LocalTime.of(10, 0))
        val result = evaluate(summaryAnchor(), preferences = preferences(quietHours = quiet))
        assertTrue(result is NotificationVerdict.Hold)
        assertEquals(quiet.endAfter(now, zone), (result as NotificationVerdict.Hold).until)
    }

    @Test fun `weekly summary outside quiet hours posts`() {
        val result = evaluate(summaryAnchor())
        assertTrue(result is NotificationVerdict.Post)
    }

    @Test fun `dormant summary produces SUMMARY_DORMANT`() {
        val result = evaluate(summaryAnchor(), dormant = true)
        assertEquals(NotificationVerdict.Discard(DiscardReason.SUMMARY_DORMANT), result)
    }

    @Test fun `dormant is ignored for non-summary categories`() {
        val result = evaluate(streakAnchor(), streak = streak(current = 3), dormant = true)
        assertTrue(result is NotificationVerdict.Post)
    }

    @Test fun `HELD ledger row does not block a later Post`() {
        val held = DeliveryRecord("k", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.HELD, null, now, at(10, 0))
        val result = evaluate(summaryAnchor(), ledger = held)
        assertTrue(result is NotificationVerdict.Post)
    }

    @Test fun `weekly summary Post carries days engaged, tasks recorded and points earned in bodyArgs`() {
        val week = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(date)
        val days = week.dates.mapIndexed { index, d ->
            com.giraffe.mizanapp.domain.week.DayCell(d, null, earned = if (index < 2) 3 else 0, available = 10, state = com.giraffe.mizanapp.domain.week.DayCellState.PARTLY_RECORDED)
        }
        val summary = com.giraffe.mizanapp.domain.week.WeekSummary(week, com.giraffe.mizanapp.domain.week.WeeklyScore(earned = 6, elapsedAvailable = 70, weekTarget = 70), days)
        val result = evaluate(summaryAnchor(), summary = summary, tasksRecorded = 4) as NotificationVerdict.Post
        assertEquals("2", result.content.bodyArgs["daysEngaged"])
        assertEquals("4", result.content.bodyArgs["tasksRecorded"])
        assertEquals("6", result.content.bodyArgs["pointsEarned"])
    }

    @Test fun `DELIVERED ledger row blocks`() {
        val delivered = DeliveryRecord("k", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, now, null)
        val result = evaluate(summaryAnchor(), ledger = delivered)
        assertEquals(NotificationVerdict.Discard(DiscardReason.ALREADY_DELIVERED), result)
    }
}
