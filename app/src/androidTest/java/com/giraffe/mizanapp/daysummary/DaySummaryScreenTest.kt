package com.giraffe.mizanapp.daysummary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The day summary is read-only by construction — this test proves it holds
 * at the rendered-node level too, not only in the type system (FR-024).
 */
@RunWith(AndroidJUnit4::class)
class DaySummaryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun readyState(): DaySummaryUiState = DaySummaryUiState(
        status = DaySummaryUiState.Status.Ready,
        sections = listOf(
            SummarySectionUi(
                id = "fajr",
                label = "Fajr",
                tasks = listOf(
                    SummaryTaskUi("fajr-1", "Fajr One", points = 2, recordedCount = 1, maxOccurrences = 1),
                ),
            ),
            SummarySectionUi(
                id = "adhkar",
                label = "Adhkar",
                tasks = listOf(
                    SummaryTaskUi("adhkar", "Adhkar Task", points = 2, recordedCount = 4, maxOccurrences = 9),
                ),
            ),
        ),
        earnedPoints = 10,
        availablePoints = 20,
    )

    @Test
    fun every_section_and_task_label_is_displayed() {
        compose.setContent { DaySummaryScreen(state = readyState()) }

        compose.onNodeWithText("Fajr").assertExists()
        compose.onNodeWithText("Fajr One").assertExists()
        compose.onNodeWithText("Adhkar Task", substring = false).assertExists()
    }

    @Test
    fun a_multi_occurrence_task_shows_its_recorded_count_against_its_limit() {
        compose.setContent { DaySummaryScreen(state = readyState()) }

        compose.onNodeWithText("4/9", substring = true).assertExists()
    }

    @Test
    fun nothing_on_screen_is_clickable() {
        compose.setContent { DaySummaryScreen(state = readyState()) }

        // A screen with no way to complete, undo, add, delete, or edit
        // anything has no clickable node at all.
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun no_forbidden_action_labels_appear() {
        compose.setContent { DaySummaryScreen(state = readyState()) }

        val forbidden = SemanticsMatcher("has a forbidden action label") { node ->
            val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
            listOf("Complete", "Undo", "Add", "Delete", "Edit").any { text.contains(it) }
        }
        compose.onAllNodes(forbidden).assertCountEquals(0)
    }

    @Test
    fun no_record_state_shows_a_plain_statement_not_an_error() {
        compose.setContent { DaySummaryScreen(state = DaySummaryUiState(status = DaySummaryUiState.Status.NoRecord)) }

        compose.onNodeWithText("recorded", substring = true).assertExists()
    }
}
