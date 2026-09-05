package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.PlanOrigin
import com.giraffe.mizanapp.domain.day.PlannedTask
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildNotificationPlanTest {

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private val now: Instant = at(9, 0)
    private val dayEndsAt: Instant = at(23, 50)

    private fun at(hour: Int, minute: Int, day: LocalDate = date): Instant =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun boundary(resolvedDate: LocalDate = date, expiresAt: Instant = dayEndsAt) = BoundaryState(
        regime = BoundaryRegime.Maghrib,
        coordinates = com.giraffe.mizanapp.domain.prayer.Coordinates(30.0, 31.0),
        zoneIdWhenObtained = zone.id,
        resolvedDate = resolvedDate,
        expiresAt = expiresAt,
        lastResolvedDate = resolvedDate,
        lastResolvedRegime = BoundaryRegime.Maghrib,
    )

    private fun prayerTimes() = PrayerTimes(
        date = date,
        fajr = at(4, 30),
        dhuhr = at(12, 0),
        asr = at(15, 30),
        maghrib = at(18, 0),
        isha = at(19, 30),
    )

    private fun task(sectionId: String, slug: String = "$sectionId-1", order: Int = 1) = PlannedTask(
        id = "$slug-id",
        dayPlanId = "plan",
        taskSlug = slug,
        sectionId = sectionId,
        sectionLabel = sectionId,
        sectionOrder = order,
        displayPosition = 1,
        label = sectionId,
        points = 1,
        maxOccurrencesPerDay = 1,
    )

    private fun dayPlan() = DayPlan(
        id = "plan",
        date = date,
        catalogueVersion = 1,
        hijriLabel = "H",
        availablePoints = 5,
        plannedTasks = listOf(
            task("fajr", order = 1),
            task("dhuhr", order = 2),
            task("asr", order = 3),
            task("maghrib", order = 4),
            task("isha", order = 5),
        ),
        origin = PlanOrigin.OPENED,
    )

    private fun streak(current: Int = 0, todayCounted: Boolean = false) = StreakSummary(
        current = current,
        longest = current,
        lastActiveDate = if (current > 0) date.minusDays(1) else null,
        todayCounted = todayCounted,
        recentActivity = List(7) {
            com.giraffe.mizanapp.domain.streak.ActivityDay(
                date.minusDays(it.toLong()),
                if (it == 0 && todayCounted) com.giraffe.mizanapp.domain.streak.ActivityState.COUNTED else com.giraffe.mizanapp.domain.streak.ActivityState.NOT_RECORDED,
            )
        },
        showBreakNotice = false,
        isAtRisk = current >= 1 && !todayCounted,
    )

    private fun preferences(
        enabled: Set<NotificationCategory> = setOf(NotificationCategory.PRAYER_WINDOW, NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY),
        allSilenced: Boolean = false,
    ) = NotificationPreferences(enabled, allSilenced, quietHours = null)

    private fun plan(
        now: Instant = this.now,
        boundary: BoundaryState = boundary(),
        prayerTimes: PrayerTimes? = prayerTimes(),
        dayPlan: DayPlan? = dayPlan(),
        completions: List<Completion> = emptyList(),
        streak: StreakSummary = streak(),
        preferences: NotificationPreferences = preferences(),
        weekClosesAt: Instant? = null,
        ledger: List<DeliveryRecord> = emptyList(),
        dormant: Boolean = false,
    ) = buildNotificationPlan(now, zone, boundary, prayerTimes, dayPlan, completions, streak, preferences, weekClosesAt, ledger, dormant)

    @Test fun `all silenced returns no anchors but refreshAt still set`() {
        val result = plan(preferences = preferences(allSilenced = true))
        assertTrue(result.anchors.isEmpty())
        assertEquals(dayEndsAt, result.refreshAt)
    }

    @Test fun `category absent from enabled contributes no anchor`() {
        val result = plan(
            preferences = preferences(enabled = setOf(NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY)),
            streak = streak(current = 0),
        )
        assertTrue(result.anchors.none { it.category == NotificationCategory.PRAYER_WINDOW })
    }

    @Test fun `anchor whose key is terminal in ledger is absent`() {
        val subjectDate = date
        val key = "STREAK:$subjectDate"
        val ledger = listOf(DeliveryRecord(key, NotificationCategory.STREAK_AT_RISK, DeliveryState.DELIVERED, null, now, null))
        val result = plan(streak = streak(current = 3, todayCounted = false), ledger = ledger)
        assertTrue(result.anchors.none { it.anchorKey == key })
    }

    @Test fun `anchor whose firesAt is at or before now is absent`() {
        // Streak anchor fires at nextBoundaryAfter(now, dayEndsAt); force it before now by using a now near dayEndsAt.
        val lateNow = dayEndsAt
        val result = plan(now = lateNow, streak = streak(current = 3, todayCounted = false))
        assertTrue(result.anchors.none { it.category == NotificationCategory.STREAK_AT_RISK && !it.firesAt.isAfter(lateNow) })
    }

    @Test fun `refreshAt always equals boundary expiresAt under every combination`() {
        listOf(
            preferences(allSilenced = true),
            preferences(enabled = emptySet()),
            preferences(),
        ).forEach { prefs ->
            val result = plan(preferences = prefs)
            assertEquals(dayEndsAt, result.refreshAt)
        }
    }

    // --- US1: weekly summary branch ---

    @Test fun `weekly summary anchor produced at weekClosesAt when enabled`() {
        val closesAt = at(18, 5)
        val result = plan(weekClosesAt = closesAt, now = at(9, 0))
        assertTrue(result.anchors.any { it.category == NotificationCategory.WEEKLY_SUMMARY && it.firesAt == closesAt })
    }

    @Test fun `no weekly summary anchor when weekClosesAt is null`() {
        val result = plan(weekClosesAt = null)
        assertTrue(result.anchors.none { it.category == NotificationCategory.WEEKLY_SUMMARY })
    }

    @Test fun `no weekly summary anchor when dormant`() {
        val result = plan(weekClosesAt = at(18, 5), dormant = true)
        assertTrue(result.anchors.none { it.category == NotificationCategory.WEEKLY_SUMMARY })
    }

    @Test fun `no weekly summary anchor when already terminal in ledger`() {
        val closesAt = at(18, 5)
        val weekKey = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(date).key
        val key = "WEEK:${weekKey.value}"
        val ledger = listOf(DeliveryRecord(key, NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, now, null))
        val result = plan(weekClosesAt = closesAt, ledger = ledger)
        assertTrue(result.anchors.none { it.anchorKey == key })
    }

    // --- US2: prayer window branch ---

    @Test fun `five anchors when all five sections incomplete`() {
        val result = plan(now = at(3, 0))
        val prayerAnchors = result.anchors.filter { it.category == NotificationCategory.PRAYER_WINDOW }
        assertEquals(5, prayerAnchors.size)
    }

    @Test fun `four anchors when one section is at its occurrence limits`() {
        val completions = listOf(
            Completion("c1", "plan", "fajr-1", date, 1, at(4, 40)),
        )
        val result = plan(now = at(3, 0), completions = completions)
        val prayerAnchors = result.anchors.filter { it.category == NotificationCategory.PRAYER_WINDOW }
        assertEquals(4, prayerAnchors.size)
    }

    @Test fun `zero prayer anchors when prayerTimes is null`() {
        val result = plan(prayerTimes = null, now = at(3, 0))
        assertTrue(result.anchors.none { it.category == NotificationCategory.PRAYER_WINDOW })
    }

    @Test fun `zero prayer anchors when category disabled`() {
        val result = plan(
            preferences = preferences(enabled = setOf(NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY)),
            now = at(3, 0),
        )
        assertTrue(result.anchors.none { it.category == NotificationCategory.PRAYER_WINDOW })
    }

    @Test fun `each prayer anchor windowEndsAt equals the next prayer instant`() {
        val result = plan(now = at(3, 0))
        val fajrAnchor = result.anchors.first { it.category == NotificationCategory.PRAYER_WINDOW && (it.speaksFor as AnchorSubject.PrayerWindow).sectionId == "fajr" }
        val subject = fajrAnchor.speaksFor as AnchorSubject.PrayerWindow
        assertEquals(prayerTimes().dhuhr, subject.windowEndsAt)
    }

    // --- US3: streak branch ---

    @Test fun `streak anchor at nextBoundaryAfter when live and not counted`() {
        val result = plan(streak = streak(current = 5, todayCounted = false), now = at(3, 0))
        val expected = com.giraffe.mizanapp.domain.streak.StreakClock.nextBoundaryAfter(at(3, 0), dayEndsAt)
        assertTrue(result.anchors.any { it.category == NotificationCategory.STREAK_AT_RISK && it.firesAt == expected })
    }

    @Test fun `no streak anchor when today counted`() {
        val result = plan(streak = streak(current = 5, todayCounted = true))
        assertTrue(result.anchors.none { it.category == NotificationCategory.STREAK_AT_RISK })
    }

    @Test fun `no streak anchor when current is zero`() {
        val result = plan(streak = streak(current = 0, todayCounted = false))
        assertTrue(result.anchors.none { it.category == NotificationCategory.STREAK_AT_RISK })
    }

    @Test fun `streak anchor produced on fallback regime too`() {
        val fallbackBoundary = BoundaryState(
            regime = BoundaryRegime.Fallback(com.giraffe.mizanapp.domain.time.FallbackReason.NEVER_HAD_LOCATION),
            coordinates = null,
            zoneIdWhenObtained = null,
            resolvedDate = date,
            expiresAt = dayEndsAt,
            lastResolvedDate = null,
            lastResolvedRegime = null,
        )
        val result = plan(boundary = fallbackBoundary, prayerTimes = null, streak = streak(current = 2, todayCounted = false), now = at(3, 0))
        val expected = com.giraffe.mizanapp.domain.streak.StreakClock.nextBoundaryAfter(at(3, 0), dayEndsAt)
        assertTrue(result.anchors.any { it.category == NotificationCategory.STREAK_AT_RISK && it.firesAt == expected })
    }
}
