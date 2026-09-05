package com.giraffe.mizanapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.notifications.NotificationSettings
import com.giraffe.mizanapp.notifications.PermissionState
import com.giraffe.mizanapp.notifications.notificationStatements
import com.giraffe.mizanapp.profile.ProfileScreen
import com.giraffe.mizanapp.profile.ProfileUiState
import com.giraffe.mizanapp.weeklysummary.CoverageNote
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryContent
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryScreen
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryUiState
import org.junit.Rule
import org.junit.Test

/** FR-007, FR-027: with notification permission denied and every category on, nothing crashes,
 *  the settings surface states the permission is off, and the summary screen is still fully
 *  populated -- the two are independent (Phase 5 precedent). */
class PermissionDeniedTest {

    @get:Rule val compose = createComposeRule()

    @Test fun settingsSurfaceStatesPermissionIsDeniedAndNothingCrashes() {
        val notifications = NotificationSettings(
            prayerWindowEnabled = true,
            streakAtRiskEnabled = true,
            weeklySummaryEnabled = true,
            allSilenced = false,
            quietHours = null,
            systemPermission = PermissionState.DENIED,
            deliveryMode = DeliveryMode.EXACT,
            statements = notificationStatements(PermissionState.DENIED, DeliveryMode.EXACT, false, false, false),
        )
        compose.setContent {
            ProfileScreen(state = ProfileUiState(email = "user@example.test", notifications = notifications), onEvent = {})
        }
        compose.onNodeWithText("system settings", substring = true).assertExists()
    }

    @Test fun summaryScreenIsFullyPopulatedRegardlessOfPermission() {
        val content = WeeklySummaryContent.Closed(
            weekKey = WeekKey("2026-08-29"),
            range = "Aug 29 - Sep 4",
            daysEngaged = 5,
            daysInWeek = 7,
            tasksRecorded = 20,
            pointsEarned = 60,
            pointsAvailable = 80,
            streakAtClose = 5,
            coverage = null as CoverageNote?,
            quiet = false,
        )
        compose.setContent {
            WeeklySummaryScreen(
                state = WeeklySummaryUiState(content = content, canGoEarlier = true, canGoLater = false),
                onEarlier = {},
                onLater = {},
                onOpenWeekSheet = {},
            )
        }
        compose.onNodeWithText("60", substring = true).assertExists()
    }
}
