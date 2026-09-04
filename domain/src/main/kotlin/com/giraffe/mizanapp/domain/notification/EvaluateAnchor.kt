package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.day.Completion
import com.giraffe.mizanapp.domain.day.DayPlan
import com.giraffe.mizanapp.domain.day.liveCount
import com.giraffe.mizanapp.domain.streak.StreakSummary
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.week.WeekSummary
import java.time.Instant
import java.time.ZoneId

fun evaluateAnchor(anchor: NotificationAnchor, now: Instant, zone: ZoneId, boundary: BoundaryState, plan: DayPlan?, completions: List<Completion>, streak: StreakSummary, preferences: NotificationPreferences, summary: WeekSummary?, ledger: DeliveryRecord?, hasPermission: Boolean, dormant: Boolean = false): NotificationVerdict {
    if (ledger != null && ledger.state != DeliveryState.HELD) return NotificationVerdict.Discard(DiscardReason.ALREADY_DELIVERED)
    if (!hasPermission) return NotificationVerdict.Discard(DiscardReason.NO_PERMISSION)
    if (preferences.allSilenced) return NotificationVerdict.Discard(DiscardReason.ALL_SILENCED)
    if (anchor.category !in preferences.enabled) return NotificationVerdict.Discard(DiscardReason.CATEGORY_OFF)
    val current = when (val subject = anchor.speaksFor) { is AnchorSubject.PrayerWindow -> subject.date == boundary.resolvedDate; is AnchorSubject.Day -> subject.date == boundary.resolvedDate; is AnchorSubject.ClosedWeek -> subject.key == WeekBoundary.weekContaining(boundary.resolvedDate).key }
    if (!current) return NotificationVerdict.Discard(DiscardReason.DAY_ROLLED_OVER)
    if (anchor.speaksFor is AnchorSubject.PrayerWindow) { val p = anchor.speaksFor as AnchorSubject.PrayerWindow; if (!now.isBefore(p.windowEndsAt)) return NotificationVerdict.Discard(DiscardReason.WINDOW_PASSED); if (plan?.sectionsInOrder()?.firstOrNull { it.first == p.sectionId }?.second?.all { liveCount(completions, it.taskSlug) >= it.maxOccurrencesPerDay } == true) return NotificationVerdict.Discard(DiscardReason.SECTION_COMPLETE) }
    if (anchor.category == NotificationCategory.STREAK_AT_RISK && streak.todayCounted) return NotificationVerdict.Discard(DiscardReason.DAY_ALREADY_COUNTED)
    if (anchor.category == NotificationCategory.STREAK_AT_RISK && streak.current == 0) return NotificationVerdict.Discard(DiscardReason.NO_LIVE_STREAK)
    if (anchor.category == NotificationCategory.WEEKLY_SUMMARY && dormant) return NotificationVerdict.Discard(DiscardReason.SUMMARY_DORMANT)
    preferences.quietHours?.takeIf { it.contains(now, zone) }?.let { return if (anchor.category == NotificationCategory.WEEKLY_SUMMARY) NotificationVerdict.Hold(it.endAfter(now, zone)) else NotificationVerdict.Discard(DiscardReason.QUIET_HOURS) }
    return NotificationVerdict.Post(NotificationContent(anchor.category, anchor.category.name, emptyMap(), when (val s = anchor.speaksFor) { is AnchorSubject.PrayerWindow -> "TODAY:${s.sectionId}"; is AnchorSubject.Day -> "TODAY"; is AnchorSubject.ClosedWeek -> "WEEKLYSUMMARY:${s.key.value}" }))
}
