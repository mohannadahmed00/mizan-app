package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T063: only the WEEKLY_SUMMARY mapping exists yet — prayer and streak content arrive in
 *  Phase 4/5 (T075/T076, T085/T086) and extend this same file. */
class NotificationContentMapperTest {

    private val forbidden = listOf(
        "missed", "failed", "shortfall", "lost", "didn't", "not done", "incomplete", "fell short",
    )

    private fun content(daysEngaged: Int, tasksRecorded: Int, pointsEarned: Int) = NotificationContent(
        category = NotificationCategory.WEEKLY_SUMMARY,
        titleKey = "WEEKLY_SUMMARY",
        bodyArgs = mapOf("daysEngaged" to daysEngaged.toString(), "tasksRecorded" to tasksRecorded.toString(), "pointsEarned" to pointsEarned.toString()),
        destination = "WEEKLYSUMMARY:2026-08-29",
    )

    @Test fun `body names days engaged, tasks recorded and points earned`() {
        val rendered = content(daysEngaged = 3, tasksRecorded = 12, pointsEarned = 40).render()
        assertTrue(rendered.body.contains("3"))
        assertTrue(rendered.body.contains("12"))
        assertTrue(rendered.body.contains("40"))
    }

    @Test fun `mapper is a pure function of NotificationContent and reads no clock`() {
        val a = content(2, 5, 10).render()
        val b = content(2, 5, 10).render()
        assertEquals(a, b)
    }

    @Test fun `a quiet week with zero figures still produces a body containing no forbidden word`() {
        val rendered = content(daysEngaged = 0, tasksRecorded = 0, pointsEarned = 0).render()
        forbidden.forEach { word -> assertFalse("body must not contain '$word': ${rendered.body}", rendered.body.contains(word, ignoreCase = true)) }
        forbidden.forEach { word -> assertFalse("title must not contain '$word': ${rendered.title}", rendered.title.contains(word, ignoreCase = true)) }
    }
}
