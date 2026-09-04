package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.Instant
import java.time.LocalDate

sealed interface AnchorSubject {
    data class PrayerWindow(val date: LocalDate, val sectionId: String, val windowEndsAt: Instant) : AnchorSubject
    data class Day(val date: LocalDate) : AnchorSubject
    data class ClosedWeek(val key: WeekKey) : AnchorSubject
}
data class NotificationAnchor(val category: NotificationCategory, val firesAt: Instant, val speaksFor: AnchorSubject)
val NotificationAnchor.anchorKey: String get() = when (val s = speaksFor) { is AnchorSubject.PrayerWindow -> "PRAYER:${s.date}:${s.sectionId}"; is AnchorSubject.Day -> "STREAK:${s.date}"; is AnchorSubject.ClosedWeek -> "WEEK:${s.key.value}" }
