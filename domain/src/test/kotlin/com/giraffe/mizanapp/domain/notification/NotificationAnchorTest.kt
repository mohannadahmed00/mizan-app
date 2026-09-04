package com.giraffe.mizanapp.domain.notification

import com.giraffe.mizanapp.domain.week.WeekKey
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAnchorTest {
    private val date = LocalDate.of(2026, 9, 4); private val at = Instant.parse("2026-09-04T12:00:00Z")
    @Test fun `subjects have stable keys`() {
        assertEquals("PRAYER:2026-09-04:asr", NotificationAnchor(NotificationCategory.PRAYER_WINDOW, at, AnchorSubject.PrayerWindow(date, "asr", at)).anchorKey)
        assertEquals("STREAK:2026-09-04", NotificationAnchor(NotificationCategory.STREAK_AT_RISK, at, AnchorSubject.Day(date)).anchorKey)
        assertEquals("WEEK:2026-08-29", NotificationAnchor(NotificationCategory.WEEKLY_SUMMARY, at, AnchorSubject.ClosedWeek(WeekKey("2026-08-29"))).anchorKey)
    }
}
