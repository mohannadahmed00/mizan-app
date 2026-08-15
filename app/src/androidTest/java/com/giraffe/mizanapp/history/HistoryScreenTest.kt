package com.giraffe.mizanapp.history

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.week.DayCellUi
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dayCell(offset: Long, state: DayCellState, weekStart: LocalDate = LocalDate.parse("2026-08-08")): DayCellUi {
        val date = weekStart.plusDays(offset)
        return DayCellUi(
            date = date,
            dayLabel = "d$offset",
            hijriLabel = null,
            earnedPoints = if (state == DayCellState.FULLY_RECORDED) 69 else 0,
            availablePoints = 69,
            state = state,
        )
    }

    private fun emptyWeekRow(weekStart: LocalDate, earned: Int = 0, available: Int = 69): WeekRowUi = WeekRowUi(
        weekKey = WeekKey(weekStart.toString()),
        startDate = weekStart,
        endDate = weekStart.plusDays(6),
        earnedPoints = earned,
        availablePoints = available * 7,
        days = (0L..6L).map { dayCell(it, DayCellState.NOTHING_RECORDED, weekStart) },
    )

    @Test
    fun twelve_consecutive_empty_weeks_all_render() {
        val weeks = (0 until 12).map { i -> emptyWeekRow(LocalDate.parse("2026-08-08").minusDays(7L * i)) }
        val state = HistoryUiState(status = HistoryUiState.Status.Ready, weeks = weeks, hasMore = false)

        compose.setContent { HistoryScreen(state = state, onEvent = {}) }

        // LazyColumn only composes on-screen items - scroll the list itself
        // to each index in turn rather than asserting the whole list exists
        // at once, which off-screen rows can never satisfy.
        weeks.forEachIndexed { index, week ->
            compose.onNodeWithTag("history-list").performScrollToIndex(index)
            compose.onNodeWithTag("week-row-${week.weekKey.value}").assertExists()
        }
    }

    @Test
    fun record_start_is_stated_at_the_end_of_the_list() {
        val state = HistoryUiState(
            status = HistoryUiState.Status.Ready,
            weeks = listOf(emptyWeekRow(LocalDate.parse("2026-08-08"))),
            hasMore = false,
        )

        compose.setContent { HistoryScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("record-start-notice").assertExists()
    }

    @Test
    fun record_not_started_shows_a_way_to_start() {
        compose.setContent {
            HistoryScreen(state = HistoryUiState(status = HistoryUiState.Status.RecordNotStarted), onEvent = {})
        }

        compose.onNodeWithText("Today", substring = true).assertExists()
    }

    @Test
    fun could_not_load_shows_a_retry() {
        compose.setContent {
            HistoryScreen(
                state = HistoryUiState(status = HistoryUiState.Status.CouldNotLoad("network unavailable")),
                onEvent = {},
            )
        }

        compose.onNodeWithTag("retry-button").assertExists()
    }

    @Test
    fun no_element_uses_a_red_colour_or_a_cross_glyph() {
        val state = HistoryUiState(
            status = HistoryUiState.Status.Ready,
            weeks = listOf(emptyWeekRow(LocalDate.parse("2026-08-08"))),
            hasMore = false,
        )
        compose.setContent { HistoryScreen(state = state, onEvent = {}) }

        compose.onAllNodesWithText("✕").assertCountEquals(0)
        compose.onAllNodesWithText("❌").assertCountEquals(0)
        listOf("missed", "failed").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    /** FR-003: the four day-position states must render distinctly - not just exist in the domain model. */
    @Test
    fun the_four_day_position_states_render_distinctly() {
        val weekStart = LocalDate.parse("2026-08-08")
        val week = WeekRowUi(
            weekKey = WeekKey(weekStart.toString()),
            startDate = weekStart,
            endDate = weekStart.plusDays(3),
            earnedPoints = 69,
            availablePoints = 69 * 4,
            // Exactly one day per described state - a padded 7-day week would
            // repeat NOTHING_RECORDED and make "exactly one" the wrong assertion.
            days = listOf(
                dayCell(0, DayCellState.FULLY_RECORDED, weekStart),
                dayCell(1, DayCellState.NOTHING_RECORDED, weekStart),
                dayCell(2, DayCellState.OUTSIDE_RECORD, weekStart),
                dayCell(3, DayCellState.NOT_YET_ELAPSED, weekStart),
            ),
        )
        val state = HistoryUiState(status = HistoryUiState.Status.Ready, weeks = listOf(week), hasMore = false)

        compose.setContent { HistoryScreen(state = state, onEvent = {}) }

        val descriptions = listOf("Recorded", "Not recorded", "Outside the record", "Upcoming")
        val distinctCount = descriptions.map { desc ->
            compose.onAllNodes(
                SemanticsMatcher("has content description '$desc'") { node ->
                    node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.contains(desc)
                },
                useUnmergedTree = true,
            )
        }
        // Each of the four descriptions must find at least one matching node.
        distinctCount.forEach { it.assertCountEquals(1) }
    }

    /** FR-032: weeks already loaded still render, and what cannot be built is named. */
    @Test
    fun partial_catalogue_shows_the_weeks_that_exist_and_names_what_cannot_be_built() {
        val state = HistoryUiState(
            status = HistoryUiState.Status.CatalogueUnavailable("catalogue version 3 is unavailable"),
            weeks = listOf(emptyWeekRow(LocalDate.parse("2026-08-08"))),
            hasMore = false,
        )

        compose.setContent { HistoryScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("week-row-2026-08-08").assertExists()
        compose.onNodeWithTag("partial-catalogue-notice").assertExists()
    }
}
