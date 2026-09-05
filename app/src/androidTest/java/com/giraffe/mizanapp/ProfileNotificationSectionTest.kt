package com.giraffe.mizanapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.QuietHours
import com.giraffe.mizanapp.notifications.NotificationSettings
import com.giraffe.mizanapp.notifications.PermissionState
import com.giraffe.mizanapp.notifications.notificationStatements
import com.giraffe.mizanapp.profile.NotificationSettingsEvent
import com.giraffe.mizanapp.profile.ProfileEvent
import com.giraffe.mizanapp.profile.ProfileScreen
import com.giraffe.mizanapp.profile.ProfileUiState
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class ProfileNotificationSectionTest {

    @get:Rule val compose = createComposeRule()

    private fun state(notifications: NotificationSettings) = ProfileUiState(
        email = "user@example.test",
        notifications = notifications,
    )

    @Test fun theThreeCategorySwitchesAndTheMasterSilenceSwitchRenderAndReflectState() {
        val notifications = NotificationSettings(
            prayerWindowEnabled = true,
            streakAtRiskEnabled = false,
            weeklySummaryEnabled = true,
            allSilenced = false,
            quietHours = null,
            systemPermission = PermissionState.GRANTED,
            deliveryMode = DeliveryMode.EXACT,
            statements = notificationStatements(PermissionState.GRANTED, DeliveryMode.EXACT, false, false, false),
        )
        var events = 0
        compose.setContent {
            ProfileScreen(state = state(notifications), onEvent = { events++ })
        }
        compose.onNodeWithTag("notifications-prayer-window").assertExists()
        compose.onNodeWithTag("notifications-streak-at-risk").assertExists()
        compose.onNodeWithTag("notifications-weekly-summary").assertExists()
        compose.onNodeWithTag("notifications-all-silenced").assertExists()
    }

    @Test fun everyStatementLineIsDisplayed() {
        val statements = listOf("Permission line one.", "Another line two.")
        val notifications = NotificationSettings(
            prayerWindowEnabled = false,
            streakAtRiskEnabled = false,
            weeklySummaryEnabled = true,
            allSilenced = false,
            quietHours = null,
            systemPermission = PermissionState.DENIED,
            deliveryMode = DeliveryMode.EXACT,
            statements = statements,
        )
        compose.setContent { ProfileScreen(state = state(notifications), onEvent = {}) }
        statements.forEach { line -> compose.onNodeWithText(line, substring = true).assertExists() }
    }

    @Test fun quietHoursRowShowsTheWindowWhenSetAndAnOffStateWhenNot() {
        val withWindow = NotificationSettings(
            prayerWindowEnabled = false, streakAtRiskEnabled = false, weeklySummaryEnabled = true,
            allSilenced = false, quietHours = QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
            systemPermission = PermissionState.GRANTED, deliveryMode = DeliveryMode.EXACT,
            statements = listOf("ready"),
        )
        compose.setContent { ProfileScreen(state = state(withWindow), onEvent = {}) }
        compose.onNodeWithTag("notifications-quiet-hours-row").assertExists()
        compose.onNodeWithText("22:00", substring = true).assertExists()
    }

    @Test fun togglingACategorySendsTheRightEvent() {
        var lastEvent: ProfileEvent? = null
        val notifications = NotificationSettings(
            prayerWindowEnabled = false, streakAtRiskEnabled = false, weeklySummaryEnabled = true,
            allSilenced = false, quietHours = null,
            systemPermission = PermissionState.GRANTED, deliveryMode = DeliveryMode.EXACT,
            statements = listOf("ready"),
        )
        compose.setContent { ProfileScreen(state = state(notifications), onEvent = { lastEvent = it }) }
        compose.onNodeWithTag("notifications-prayer-window").performClick()
        val event = lastEvent as? ProfileEvent.NotificationSettingsChanged
        val inner = event?.event as? NotificationSettingsEvent.SetCategory
        org.junit.Assert.assertEquals(true, inner?.enabled)
    }
}
