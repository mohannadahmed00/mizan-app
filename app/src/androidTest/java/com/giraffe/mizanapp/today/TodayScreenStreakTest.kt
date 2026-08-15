package com.giraffe.mizanapp.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class TodayScreenStreakTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sections() = listOf(
        SectionUi(
            id = "fajr",
            label = "الفجر",
            tasks = listOf(TaskRowUi(slug = "fajr-1", label = "Fajr 1", points = 2, recordedCount = 0, maxOccurrences = 1)),
        ),
        SectionUi(
            id = "dhuhr",
            label = "الظهر",
            tasks = listOf(TaskRowUi(slug = "dhuhr-1", label = "Dhuhr 1", points = 2, recordedCount = 0, maxOccurrences = 1)),
        ),
    )

    private fun readyState(index: Int = 0) = TodayUiState(
        status = TodayUiState.Status.Ready,
        civilDate = LocalDate.parse("2026-08-19"),
        hijriLabel = "1 Muharram 1448",
        sections = sections(),
        currentSectionIndex = index,
        earnedPoints = 0,
        availablePoints = 4,
        streak = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = true),
    )

    @Test
    fun element_present_on_first_block() {
        composeTestRule.setContent {
            TodayScreen(state = readyState(0), onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onNodeWithTag("streak-element").assertExists()
        composeTestRule.onNodeWithText("5", substring = true).assertExists()
    }

    @Test
    fun element_unchanged_after_stepping_forward_and_back() {
        composeTestRule.setContent {
            TodayScreen(state = readyState(1), onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onNodeWithTag("streak-element").assertExists()
        composeTestRule.onNodeWithText("5", substring = true).assertExists()
        composeTestRule.onNodeWithText("12", substring = true).assertExists()
    }

    @Test
    fun element_present_when_catalogue_unavailable() {
        val state = TodayUiState(
            status = TodayUiState.Status.CatalogueUnavailable("no catalogue applies"),
            streak = StreakPanelUi.Ready(current = 5, longest = 12, todayCounted = true),
        )
        composeTestRule.setContent {
            TodayScreen(state = state, onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onNodeWithTag("streak-element").assertExists()
    }

    @Test
    fun element_absent_while_loading() {
        composeTestRule.setContent {
            TodayScreen(state = TodayUiState(status = TodayUiState.Status.Loading), onEvent = {}, onOpenWeek = {})
        }

        composeTestRule.onAllNodesWithTag("streak-element").assertCountEquals(0)
    }
}
