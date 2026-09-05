package com.giraffe.mizanapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.notifications.NotificationSettings
import com.giraffe.mizanapp.notifications.PermissionState
import com.giraffe.mizanapp.notifications.notificationStatements
import com.giraffe.mizanapp.notifications.render
import com.giraffe.mizanapp.profile.ProfileScreen
import com.giraffe.mizanapp.profile.ProfileUiState
import com.giraffe.mizanapp.weeklysummary.CoverageNote
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryContent
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryScreen
import com.giraffe.mizanapp.weeklysummary.WeeklySummaryUiState
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * SC-009, **gating**. Every user-visible string this feature adds -- notification titles and
 * bodies, settings labels, and every line the Weekly Summary screen renders -- must contain none
 * of the loss, failure, deficit, warning or comparative vocabulary Principle IX forbids. The
 * fixture below catches the vocabulary; the actual gate is a human reading every one of these
 * strings against CLAUDE.md's Principle IX section before this feature merges.
 */
class NotificationCopyReviewTest {

    @get:Rule val compose = createComposeRule()

    private val forbidden = listOf(
        "missed", "miss", "fail", "lost", "lose", "didn't", "haven't", "not done", "incomplete",
        "fell short", "shortfall", "warning", "break", "expire", "don't", "last chance",
        "forgot", "skipped", "worse", "behind", "compared to", "than others", "problem", "error",
    )

    private fun assertClean(label: String, text: String) {
        forbidden.forEach { word ->
            assertFalse("'$label' must not contain '$word': \"$text\"", text.contains(word, ignoreCase = true))
        }
    }

    @Test fun everyNotificationRenderingIsFreeOfForbiddenVocabulary() {
        val summary = NotificationContent(
            NotificationCategory.WEEKLY_SUMMARY, "WEEKLY_SUMMARY",
            mapOf("daysEngaged" to "0", "tasksRecorded" to "0", "pointsEarned" to "0"), "WEEKLYSUMMARY",
        ).render()
        val prayer = NotificationContent(
            NotificationCategory.PRAYER_WINDOW, "PRAYER_WINDOW",
            mapOf("section" to "asr", "remaining" to "2"), "TODAY:asr",
        ).render()
        val streak = NotificationContent(
            NotificationCategory.STREAK_AT_RISK, "STREAK_AT_RISK", mapOf("current" to "12"), "TODAY",
        ).render()

        listOf("summary" to summary, "prayer" to prayer, "streak" to streak).forEach { (label, rendered) ->
            assertClean("$label.title", rendered.title)
            assertClean("$label.body", rendered.body)
        }
    }

    @Test fun everySettingsStatementCombinationIsFreeOfForbiddenVocabulary() {
        val permissions = PermissionState.entries
        val modes = DeliveryMode.entries
        for (permission in permissions) for (mode in modes) for (needsLocation in listOf(true, false)) for (dormant in listOf(true, false)) for (silenced in listOf(true, false)) {
            notificationStatements(permission, mode, needsLocation, dormant, silenced).forEach { line ->
                assertClean("statement", line)
            }
        }
    }

    @Test fun everyLineOnTheNotificationsSettingsSectionIsFreeOfForbiddenVocabulary() {
        val notifications = NotificationSettings(
            prayerWindowEnabled = true, streakAtRiskEnabled = true, weeklySummaryEnabled = true,
            allSilenced = false, quietHours = null, systemPermission = PermissionState.DENIED,
            deliveryMode = DeliveryMode.RELAXED,
            statements = notificationStatements(PermissionState.DENIED, DeliveryMode.RELAXED, true, true, false),
        )
        compose.setContent {
            ProfileScreen(state = ProfileUiState(email = "user@example.test", notifications = notifications), onEvent = {})
        }
        assertClean("profile screen", compose.onRoot().printToString())
    }

    @Test fun everyLineOnTheWeeklySummaryScreenIsFreeOfForbiddenVocabulary() {
        val quiet = WeeklySummaryContent.Closed(
            weekKey = WeekKey("2026-08-29"), range = "Aug 29 - Sep 4", daysEngaged = 0, daysInWeek = 7,
            tasksRecorded = 0, pointsEarned = 0, pointsAvailable = 40, streakAtClose = 0,
            coverage = CoverageNote(LocalDate.of(2026, 8, 31)), quiet = true,
        )
        compose.setContent {
            WeeklySummaryScreen(
                state = WeeklySummaryUiState(content = quiet, canGoEarlier = true, canGoLater = false),
                onEarlier = {}, onLater = {}, onOpenWeekSheet = {},
            )
        }
        assertClean("weekly summary screen (quiet)", compose.onRoot().printToString())

        val waiting = WeeklySummaryContent.Waiting(firstSummaryAt = LocalDate.of(2026, 9, 11))
        compose.setContent {
            WeeklySummaryScreen(
                state = WeeklySummaryUiState(content = waiting, canGoEarlier = false, canGoLater = false),
                onEarlier = {}, onLater = {}, onOpenWeekSheet = {},
            )
        }
        assertClean("weekly summary screen (waiting)", compose.onRoot().printToString())
    }
}
