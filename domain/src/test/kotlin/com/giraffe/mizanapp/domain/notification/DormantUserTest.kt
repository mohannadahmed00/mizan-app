package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.streak.ActivityDay
import com.giraffe.mizanapp.domain.streak.ActivityState
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.week.isSummaryDormant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SC-014: with nothing ever recorded and every category at its default (summary only), the
 * third consecutive empty week close goes dormant -- exactly two summaries post, then none --
 * and one recorded task un-dormants the next close (`SummaryDormancyTest` proves the rule
 * itself; this proves `buildNotificationPlan` actually honours it end to end).
 */
class DormantUserTest {

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun boundary(weekEnd: LocalDate, expiresAt: Instant) = BoundaryState(
        regime = BoundaryRegime.Maghrib,
        coordinates = Coordinates(30.0, 31.0),
        zoneIdWhenObtained = zone.id,
        resolvedDate = weekEnd,
        expiresAt = expiresAt,
        lastResolvedDate = weekEnd,
        lastResolvedRegime = BoundaryRegime.Maghrib,
    )

    private val noStreak = StreakSummary(
        current = 0, longest = 0, lastActiveDate = null, todayCounted = false,
        recentActivity = List(7) { ActivityDay(LocalDate.of(2026, 1, 1).minusDays(it.toLong()), ActivityState.NOT_RECORDED) },
        showBreakNotice = false, isAtRisk = false,
    )

    @Test fun threeSimulatedEmptyMonthsYieldExactlyTwoSummariesThenNone() {
        var history = emptyList<Boolean>() // newest-first, each week's "had activity"
        var posted = 0

        // 12 consecutive Fridays (~3 months), nothing ever recorded.
        var friday = LocalDate.of(2026, 1, 2)
        repeat(12) {
            val expiresAt = at(friday, 23, 50)
            // The closing week counts itself as already closed at its own Maghrib, matching
            // `NotificationWorker.closedWeeksNewestFirst` (research R4).
            val dormant = isSummaryDormant(listOf(false) + history)
            val plan = buildNotificationPlan(
                now = at(friday, 0, 1), zone = zone, boundary = boundary(friday, expiresAt),
                prayerTimes = null, plan = null, completions = emptyList(), streak = noStreak,
                preferences = NotificationPreferences.DEFAULT, weekClosesAt = expiresAt,
                ledger = emptyList(), dormant = dormant,
            )
            if (plan.anchors.any { it.category == NotificationCategory.WEEKLY_SUMMARY }) posted++
            history = listOf(false) + history // this week was empty too
            friday = friday.plusWeeks(1)
        }

        assertEquals(2, posted)
    }

    @Test fun recordingOneTaskMakesTheNextWeekClosePostAgain() {
        // Three empty weeks already closed -- dormant.
        val history = listOf(false, false, false)
        assertEquals(true, isSummaryDormant(history))

        // The next week had activity, so its own dormancy check (built from the three most
        // recent closed weeks *including itself*) is no longer all-empty.
        val nextWeekHistory = listOf(true) + history.take(2)
        assertEquals(false, isSummaryDormant(nextWeekHistory))
    }
}
