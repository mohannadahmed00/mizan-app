package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.streak.StreakClock
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryState
import java.time.Instant
import java.time.ZoneId

data class NotificationPlan(val anchors: List<NotificationAnchor>, val refreshAt: Instant)

fun buildNotificationPlan(now: Instant, zone: ZoneId, boundary: BoundaryState, prayerTimes: PrayerTimes?, plan: DayPlan?, completions: List<Completion>, streak: StreakSummary, preferences: NotificationPreferences, weekClosesAt: Instant?, ledger: List<DeliveryRecord>, dormant: Boolean = false): NotificationPlan {
    if (preferences.allSilenced) return NotificationPlan(emptyList(), boundary.expiresAt)
    val anchors = mutableListOf<NotificationAnchor>()
    if (NotificationCategory.PRAYER_WINDOW in preferences.enabled && prayerTimes != null && plan != null) {
        plan.sectionsInOrder().forEach { (section, tasks) ->
            val prayer = prayerInstantFor(section, prayerTimes) ?: return@forEach
            val end = nextPrayerAfter(section, prayerTimes) ?: boundary.expiresAt
            val fire = NudgeWindow.firesAt(prayer, end) ?: return@forEach
            if (tasks.any { liveCount(completions, it.taskSlug) < it.maxOccurrencesPerDay }) anchors += NotificationAnchor(NotificationCategory.PRAYER_WINDOW, fire, AnchorSubject.PrayerWindow(boundary.resolvedDate, section, end))
        }
    }
    if (NotificationCategory.STREAK_AT_RISK in preferences.enabled && streak.current >= 1 && !streak.todayCounted) anchors += NotificationAnchor(NotificationCategory.STREAK_AT_RISK, StreakClock.nextBoundaryAfter(now, boundary.expiresAt), AnchorSubject.Day(boundary.resolvedDate))
    if (NotificationCategory.WEEKLY_SUMMARY in preferences.enabled && weekClosesAt != null && !dormant) anchors += NotificationAnchor(NotificationCategory.WEEKLY_SUMMARY, weekClosesAt, AnchorSubject.ClosedWeek(com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(boundary.resolvedDate).key))
    return NotificationPlan(anchors.filter { it.firesAt.isAfter(now) && ledger.terminalFor(it.anchorKey) == null }.sortedBy { it.firesAt }, boundary.expiresAt)
}
