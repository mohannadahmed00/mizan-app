package com.giraffe.mizanapp.notifications

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T061: the gating rule for the dismissible, non-blocking permission prompt (FR-007a). Asked
 * once, at the first week close, framed as the summary that is ready -- never during the first
 * week, and never a second time once asked.
 */
class NotificationPermissionPromptTest {

    private val recordStartsWithinCurrentWeek = LocalDate.of(2026, 9, 1) // a Tuesday, this week
    private val today = LocalDate.of(2026, 9, 4) // same week

    @Test fun `not shown during the first week, whatever else is true`() {
        assertEquals(
            false,
            shouldShowNotificationPermissionPrompt(recordStart = recordStartsWithinCurrentWeek, today = today, permissionAskedAt = null),
        )
    }

    @Test fun `shown once the first week has closed and permissionAskedAt is null`() {
        val recordStart = today.minusDays(14) // a full week has already closed since then
        assertTrue(shouldShowNotificationPermissionPrompt(recordStart = recordStart, today = today, permissionAskedAt = null))
    }

    @Test fun `never shown a second time once permissionAskedAt is set`() {
        val recordStart = today.minusDays(14)
        assertEquals(
            false,
            shouldShowNotificationPermissionPrompt(recordStart = recordStart, today = today, permissionAskedAt = Instant.parse("2026-08-30T09:00:00Z")),
        )
    }

    @Test fun `dismissing leaves the gate closed just like accepting would`() {
        val recordStart = today.minusDays(14)
        // "Dismiss" and "enable" both record permissionAskedAt -- the prompt never nags again
        // regardless of the answer (FR-007). The app and the summary screen are unaffected by
        // either outcome; nothing here gates their own state.
        val afterDismissal = Instant.parse("2026-09-04T10:00:00Z")
        assertEquals(false, shouldShowNotificationPermissionPrompt(recordStart, today, afterDismissal))
    }

    @Test fun `no record yet never shows the prompt`() {
        assertEquals(false, shouldShowNotificationPermissionPrompt(recordStart = null, today = today, permissionAskedAt = null))
    }
}
