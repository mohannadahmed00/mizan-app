package com.giraffe.mizanapp.insights

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.domain.week.WeekKey
import com.giraffe.mizanapp.week.DayCellUi
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Trend-view coverage for User Story 1. Later user stories append more test
 * methods to this same file rather than creating new ones (`006` tasks.md
 * convention) — see [InsightsScreenMonthTest]/[InsightsScreenSectionsTest]-
 * style additions landing in later phases.
 */
@RunWith(AndroidJUnit4::class)
class InsightsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun point(weekStart: LocalDate, percentage: Int, isInProgress: Boolean = false) = TrendPointUi(
        weekKey = WeekKey(weekStart.toString()),
        startDate = weekStart,
        percentage = percentage,
        isInProgress = isInProgress,
    )

    @Test
    fun ready_renders_one_bar_per_trend_point() {
        val points = listOf(
            point(LocalDate.parse("2026-07-25"), 50),
            point(LocalDate.parse("2026-08-01"), 80),
            point(LocalDate.parse("2026-08-08"), 100),
        )
        val state = InsightsUiState(status = InsightsUiState.Status.Ready, trend = points)

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        points.forEach { p ->
            compose.onNodeWithTag("trend-bar-${p.weekKey.value}").assertExists()
        }
    }

    @Test
    fun record_not_started_shows_explanatory_text_and_no_chart() {
        compose.setContent {
            InsightsScreen(state = InsightsUiState(status = InsightsUiState.Status.RecordNotStarted), onEvent = {})
        }

        compose.onNodeWithTag("record-not-started-notice").assertExists()
    }

    @Test
    fun could_not_load_shows_a_retry_that_emits_retry() {
        var retried = false
        compose.setContent {
            InsightsScreen(
                state = InsightsUiState(status = InsightsUiState.Status.CouldNotLoad("network unavailable")),
                onEvent = { if (it is InsightsEvent.Retry) retried = true },
            )
        }

        compose.onNodeWithTag("retry-button").performClick()
        assert(retried)
    }

    @Test
    fun load_earlier_trend_control_emits_the_event() {
        var loadEarlierRequested = false
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            trend = listOf(point(LocalDate.parse("2026-08-08"), 60)),
            trendHasMore = true,
        )

        compose.setContent {
            InsightsScreen(
                state = state,
                onEvent = { if (it is InsightsEvent.LoadEarlierTrend) loadEarlierRequested = true },
            )
        }
        compose.onNodeWithTag("load-earlier-trend-button").performClick()
        assert(loadEarlierRequested)
    }

    @Test
    fun load_earlier_trend_control_is_hidden_at_the_boundary() {
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            trend = listOf(point(LocalDate.parse("2026-08-08"), 60)),
            trendHasMore = false,
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("trend-record-start-notice").assertExists()
    }

    @Test
    fun loading_earlier_trend_keeps_existing_bars_on_screen() {
        val points = listOf(point(LocalDate.parse("2026-08-08"), 60))
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            trend = points,
            trendHasMore = true,
            isLoadingEarlierTrend = true,
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("loading-earlier-trend-indicator").assertExists()
        compose.onNodeWithTag("trend-bar-${points.single().weekKey.value}").assertExists()
    }

    @Test
    fun no_element_uses_a_red_colour_or_shame_language() {
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            trend = listOf(point(LocalDate.parse("2026-08-08"), 10, isInProgress = false)),
        )
        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onAllNodesWithText("✕").assertCountEquals(0)
        listOf("missed", "worst", "failed").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun view_switcher_selects_trend_by_default() {
        val state = InsightsUiState(status = InsightsUiState.Status.Ready, selectedView = InsightsView.TREND)
        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("select-view-trend").assertExists()
        compose.onNodeWithTag("trend-chart").assertExists()
    }

    // --- User Story 2: Monthly overview ---

    private fun monthDayCell(date: LocalDate, state: DayCellState) = DayCellUi(
        date = date,
        dayLabel = date.dayOfMonth.toString(),
        hijriLabel = null,
        earnedPoints = 0,
        availablePoints = 69,
        state = state,
    )

    @Test
    fun selecting_month_renders_a_grid_with_one_cell_per_day_of_the_month() {
        val month = YearMonth.of(2026, 8) // 31 days
        val days = (1..31).map { monthDayCell(month.atDay(it), DayCellState.NOTHING_RECORDED) }
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.MONTH,
            month = MonthOverviewUi(month = month, days = days, canGoEarlier = true, canGoLater = true),
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        days.forEach { day ->
            compose.onNodeWithTag("month-day-${day.date}").assertExists()
        }
    }

    @Test
    fun outside_record_cells_render_distinctly_from_nothing_recorded_cells() {
        val month = YearMonth.of(2026, 8)
        val days = listOf(
            monthDayCell(month.atDay(1), DayCellState.OUTSIDE_RECORD),
            monthDayCell(month.atDay(2), DayCellState.NOTHING_RECORDED),
        )
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.MONTH,
            month = MonthOverviewUi(month = month, days = days, canGoEarlier = false, canGoLater = false),
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        // Distinct testTags per state prove the two cells are not rendered identically.
        compose.onNodeWithTag("month-day-${days[0].date}-outside-record").assertExists()
        compose.onNodeWithTag("month-day-${days[1].date}-nothing-recorded").assertExists()
    }

    @Test
    fun previous_and_next_month_controls_emit_navigation_events() {
        val month = YearMonth.of(2026, 8)
        var previousRequested = false
        var nextRequested = false
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.MONTH,
            month = MonthOverviewUi(month = month, days = emptyList(), canGoEarlier = true, canGoLater = true),
        )

        compose.setContent {
            InsightsScreen(
                state = state,
                onEvent = {
                    if (it is InsightsEvent.PreviousMonth) previousRequested = true
                    if (it is InsightsEvent.NextMonth) nextRequested = true
                },
            )
        }

        compose.onNodeWithTag("previous-month-button").performClick()
        compose.onNodeWithTag("next-month-button").performClick()
        assert(previousRequested)
        assert(nextRequested)
    }

    @Test
    fun previous_month_control_is_disabled_at_the_record_start_month() {
        val month = YearMonth.of(2026, 8)
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.MONTH,
            month = MonthOverviewUi(month = month, days = emptyList(), canGoEarlier = false, canGoLater = true),
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("previous-month-button").assertIsNotEnabled()
    }

    @Test
    fun no_red_colour_anywhere_in_the_month_grid() {
        val month = YearMonth.of(2026, 8)
        val days = (1..5).map { monthDayCell(month.atDay(it), DayCellState.OUTSIDE_RECORD) }
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.MONTH,
            month = MonthOverviewUi(month = month, days = days, canGoEarlier = false, canGoLater = false),
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        listOf("missed", "worst", "failed").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    // --- User Story 3: Section breakdown and personal bests ---

    @Test
    fun sections_render_one_row_per_section_in_the_order_given() {
        val sections = listOf(
            SectionRowUi("Fajr", 92),
            SectionRowUi("Dhuhr", 88),
            SectionRowUi("Adhkar", 61),
        )
        val state = InsightsUiState(
            status = InsightsUiState.Status.Ready,
            selectedView = InsightsView.SECTIONS,
            sections = sections,
        )

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        sections.forEachIndexed { index, section ->
            compose.onNodeWithTag("section-row-$index").assertExists()
        }
    }

    @Test
    fun no_section_row_carries_a_lowest_or_worst_marker() {
        val sections = listOf(SectionRowUi("Fajr", 92), SectionRowUi("Adhkar", 10))
        val state = InsightsUiState(status = InsightsUiState.Status.Ready, selectedView = InsightsView.SECTIONS, sections = sections)

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        listOf("lowest", "worst", "least").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
        compose.onAllNodesWithTag("lowest-section-badge").assertCountEquals(0)
    }

    @Test
    fun personal_bests_card_shows_only_best_day_and_best_week_never_worst() {
        val bests = PersonalBestsUi(
            bestDay = BestDayUi(date = LocalDate.parse("2026-08-05"), hijriLabel = null, percentage = 100),
            bestWeek = BestWeekUi(startDate = LocalDate.parse("2026-08-01"), endDate = LocalDate.parse("2026-08-07"), percentage = 95),
        )
        val state = InsightsUiState(status = InsightsUiState.Status.Ready, personalBests = bests)

        compose.setContent { InsightsScreen(state = state, onEvent = {}) }

        compose.onNodeWithTag("personal-best-day").assertExists()
        compose.onNodeWithTag("personal-best-week").assertExists()
        listOf("worst", "lowest").forEach { forbidden ->
            compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun no_red_colour_or_shame_language_anywhere_across_all_three_views() {
        val trend = listOf(point(LocalDate.parse("2026-08-08"), 10))
        val month = MonthOverviewUi(
            month = YearMonth.of(2026, 8),
            days = listOf(monthDayCell(LocalDate.parse("2026-08-01"), DayCellState.NOTHING_RECORDED)),
            canGoEarlier = false,
            canGoLater = false,
        )
        val sections = listOf(SectionRowUi("Fajr", 5))
        val bests = PersonalBestsUi(
            bestDay = BestDayUi(LocalDate.parse("2026-08-05"), null, 40),
            bestWeek = null,
        )

        val stateHolder = androidx.compose.runtime.mutableStateOf(
            InsightsUiState(
                status = InsightsUiState.Status.Ready,
                selectedView = InsightsView.TREND,
                trend = trend,
                month = month,
                sections = sections,
                personalBests = bests,
            ),
        )
        compose.setContent {
            val state by stateHolder
            InsightsScreen(state = state, onEvent = {})
        }

        for (view in InsightsView.entries) {
            stateHolder.value = stateHolder.value.copy(selectedView = view)
            compose.waitForIdle()

            listOf("missed", "worst", "failed", "lowest").forEach { forbidden ->
                compose.onAllNodesWithText(forbidden, substring = true, ignoreCase = true).assertCountEquals(0)
            }
        }
    }
}
