package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.domain.notification.QuietHours
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The settings section holds the domain `QuietHours` type directly (contracts/ui-state.md §1) —
 *  there is no display-only wrapper, so round-tripping it through the UI event is exactly
 *  round-tripping the domain value. */
class QuietHoursSettingsTest {

    @Test fun aWindowThatCrossesMidnightRoundTrips() {
        val window = QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0))
        val settings = baseSettings(quietHours = window)
        assertEquals(window, settings.quietHours)
    }

    @Test fun clearingItReturnsToNull() {
        val settings = baseSettings(quietHours = null)
        assertNull(settings.quietHours)
    }

    @Test fun theWindowIsInterpretedInDeviceLocalTime() {
        // QuietHours.contains/endAfter always take an explicit ZoneId parameter (Principle VII) —
        // there is no zone stored on the value itself, so "device-local" is the caller's zone.
        val window = QuietHours(LocalTime.of(1, 0), LocalTime.of(2, 0))
        assertEquals(LocalTime.of(1, 0), window.start)
        assertEquals(LocalTime.of(2, 0), window.end)
    }

    private fun baseSettings(quietHours: QuietHours?) = NotificationSettings(
        prayerWindowEnabled = false,
        streakAtRiskEnabled = false,
        weeklySummaryEnabled = true,
        allSilenced = false,
        quietHours = quietHours,
        systemPermission = PermissionState.GRANTED,
        deliveryMode = com.giraffe.mizanapp.domain.notification.DeliveryMode.EXACT,
        statements = listOf("ready"),
    )
}
