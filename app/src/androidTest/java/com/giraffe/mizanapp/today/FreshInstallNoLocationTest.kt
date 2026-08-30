package com.giraffe.mizanapp.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Quickstart Scenario 3, US3, SC-004, SC-010, SC-017: a fresh install with no location must
 * render immediately and record normally, with no system permission dialog raised and no wait
 * for a fix. `requestLocation()` is reached only from [TodayEvent.EnableLocation] (see
 * `TodayViewModel.onEvent`), so a test that never fires that event and still records is the
 * behavioural proof that nothing here asks for permission on its own.
 */
class FreshInstallNoLocationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun freshInstallState() = TodayUiState(
        status = TodayUiState.Status.Ready,
        civilDate = LocalDate.parse("2026-08-19"),
        hijriLabel = "1 Muharram 1448",
        sections = listOf(
            SectionUi(
                id = "fajr",
                label = "الفجر",
                tasks = listOf(
                    TaskRowUi(slug = "fajr-1", label = "Fajr 1", points = 2, recordedCount = 0, maxOccurrences = 1),
                ),
            ),
        ),
        earnedPoints = 0,
        availablePoints = 2,
        streak = StreakPanelUi.Ready(current = 0, longest = 0, todayCounted = false),
        locationPrompt = LocationPrompt(
            visible = true,
            explanation = "Turning on location gives accurate local prayer times.",
        ),
    )

    @Test
    fun theScreenRendersImmediatelyWithNoLocation() {
        composeTestRule.setContent {
            TodayScreen(state = freshInstallState(), onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onNodeWithText("الفجر").assertExists()
        composeTestRule.onNodeWithTag("streak-element").assertExists()
    }

    @Test
    fun recordingWorksWithNoLocationAndNoPermissionEventEverFires() {
        val events = mutableListOf<TodayEvent>()
        composeTestRule.setContent {
            TodayScreen(state = freshInstallState(), onEvent = { events += it }, onOpenWeek = {})
        }

        composeTestRule.onNodeWithText("Fajr 1").performClick()

        assertEquals(listOf(TodayEvent.CompleteTask("fajr-1")), events)
        assertFalse("EnableLocation must never fire on its own", events.contains(TodayEvent.EnableLocation))
    }

    @Test
    fun dismissingThePromptNeverFiresEnableLocation() {
        val events = mutableListOf<TodayEvent>()
        composeTestRule.setContent {
            TodayScreen(state = freshInstallState(), onEvent = { events += it }, onOpenWeek = {})
        }

        composeTestRule.onNodeWithText("Not now").performClick()

        assertEquals(listOf(TodayEvent.DismissLocationPrompt), events)
    }

    @Test
    fun noWarningOrFailureFramingAppearsAnywhere() {
        composeTestRule.setContent {
            TodayScreen(state = freshInstallState(), onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onAllNodesWithText("error", substring = true, ignoreCase = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("denied", substring = true, ignoreCase = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("missed", substring = true, ignoreCase = true).assertCountEquals(0)
    }
}
