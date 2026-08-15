package com.giraffe.mizanapp.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.week.DayCellState
import com.giraffe.mizanapp.ui.containerColorFor
import com.giraffe.mizanapp.week.DayCellUi

/**
 * The Insights screen: three switchable views (trend, month, sections) plus
 * a personal-bests card. Read-only end to end — [InsightsEvent] carries no
 * case that could record, undo, add, remove, reorder, export, or share
 * anything (Principle VI). **Principle IX** — no red anywhere, no "worst"
 * surface, no ranking; the section list (added in `006` User Story 3) is
 * always plain catalogue order.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val status = state.status) {
        is InsightsUiState.Status.Loading -> LoadingState(modifier)
        is InsightsUiState.Status.RecordNotStarted -> RecordNotStartedState(modifier)
        is InsightsUiState.Status.CouldNotLoad -> CouldNotLoadState(status, onEvent, modifier)
        is InsightsUiState.Status.CatalogueUnavailable -> ReadyState(state, onEvent, modifier, partialNotice = status.detail)
        is InsightsUiState.Status.Ready -> ReadyState(state, onEvent, modifier)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RecordNotStartedState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing recorded yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("record-not-started-notice"),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Complete a task today to start building your insights.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CouldNotLoadState(
    status: InsightsUiState.Status.CouldNotLoad,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Insights couldn't load right now.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(status.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = { onEvent(InsightsEvent.Retry) },
            modifier = Modifier.testTag("retry-button"),
        ) { Text("Retry") }
    }
}

@Composable
private fun ReadyState(
    state: InsightsUiState,
    onEvent: (InsightsEvent) -> Unit,
    modifier: Modifier = Modifier,
    partialNotice: String? = null,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        ViewSwitcher(state.selectedView, onEvent)
        Spacer(Modifier.width(16.dp))

        partialNotice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("partial-catalogue-notice").padding(bottom = 8.dp),
            )
        }

        PersonalBestsCard(state.personalBests)
        Spacer(Modifier.width(16.dp))

        when (state.selectedView) {
            InsightsView.TREND -> TrendChart(state, onEvent)
            InsightsView.MONTH -> MonthGrid(state.month, onEvent)
            InsightsView.SECTIONS -> SectionsList(state.sections, onEvent)
        }
    }
}

/**
 * Shown regardless of [InsightsUiState.selectedView] — the personal-bests
 * card is a standing summary, not one more switchable view. Renders only a
 * best day and a best week; there is no "worst" field anywhere in
 * [PersonalBestsUi] for it to render (Principle IX, spec.md Assumptions).
 */
@Composable
private fun PersonalBestsCard(bests: PersonalBestsUi?) {
    if (bests == null || (bests.bestDay == null && bests.bestWeek == null)) return

    Column(Modifier.fillMaxWidth().testTag("personal-bests-card")) {
        Text("Personal bests", style = MaterialTheme.typography.titleSmall)
        bests.bestDay?.let { day ->
            Text(
                "Best day: ${day.date} — ${day.percentage}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("personal-best-day"),
            )
        }
        bests.bestWeek?.let { week ->
            Text(
                "Best week: ${week.startDate}–${week.endDate} — ${week.percentage}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("personal-best-week"),
            )
        }
    }
}

/**
 * Every section listed in the order [sections] is already in — catalogue
 * order, never sorted by rate, never badged as lowest (Clarification Q2,
 * FR-003, FR-010).
 */
@Composable
private fun SectionsList(sections: List<SectionRowUi>, onEvent: (InsightsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("sections-list")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(
                onClick = { onEvent(InsightsEvent.SwitchSectionPeriod(toMonth = false)) },
                modifier = Modifier.testTag("sections-scope-week"),
            ) { Text("This week") }
            TextButton(
                onClick = { onEvent(InsightsEvent.SwitchSectionPeriod(toMonth = true)) },
                modifier = Modifier.testTag("sections-scope-month"),
            ) { Text("This month") }
        }
        Spacer(Modifier.width(8.dp))
        sections.forEachIndexed { index, section ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("section-row-$index"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(section.sectionLabel, style = MaterialTheme.typography.bodyMedium)
                Text("${section.percentage}%", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ViewSwitcher(selected: InsightsView, onEvent: (InsightsEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        InsightsView.entries.forEach { view ->
            TextButton(
                onClick = { onEvent(InsightsEvent.SelectView(view)) },
                modifier = Modifier.testTag("select-view-${view.name.lowercase()}"),
            ) {
                Text(
                    text = view.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = if (view == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * A plain Compose bar row — no charting library (research.md, plan.md
 * Technical Context). Every bar is a shade of the primary green; an
 * in-progress week gets an outline instead of a solid fill so it can never
 * be misread as a low-consistency completed week (FR-008, Clarification Q3).
 */
@Composable
private fun TrendChart(state: InsightsUiState, onEvent: (InsightsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(160.dp).testTag("trend-chart"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.trend.forEach { point -> TrendBar(point) }
        }

        Spacer(Modifier.width(8.dp))

        if (state.isLoadingEarlierTrend) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("loading-earlier-trend-indicator"))
            }
        } else if (state.trendHasMore) {
            TextButton(
                onClick = { onEvent(InsightsEvent.LoadEarlierTrend) },
                modifier = Modifier.testTag("load-earlier-trend-button"),
            ) { Text("Load earlier weeks") }
        } else {
            Text(
                "You've reached the beginning of your record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("trend-record-start-notice"),
            )
        }
    }
}

@Composable
private fun TrendBar(point: TrendPointUi) {
    val heightFraction = (point.percentage / 100f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(24.dp)
            .fillMaxSizeHeightFraction(heightFraction)
            .testTag("trend-bar-${point.weekKey.value}")
            .semantics { contentDescription = "Week of ${point.startDate}: ${point.percentage} percent" }
            .then(
                if (point.isInProgress) {
                    Modifier.border(
                        BorderStroke(2.dp, containerColorFor(DayCellState.FULLY_RECORDED)),
                        RoundedCornerShape(4.dp),
                    )
                } else {
                    Modifier.background(containerColorFor(DayCellState.FULLY_RECORDED), RoundedCornerShape(4.dp))
                },
            ),
    )
}

/** A `Column`/`Row` weight can't express "this fraction of the parent's fixed height" directly. */
private fun Modifier.fillMaxSizeHeightFraction(fraction: Float): Modifier =
    this.height((160 * fraction.coerceIn(0.02f, 1f)).dp)

/**
 * A calendar-style grid, one cell per day of the loaded month. Reuses
 * [DayCellUi]/[DayCellState] exactly as the Week screen does (research.md
 * R3) — the same four-plus-one states, the same color mapping, no second
 * table to audit for SC-006.
 */
@Composable
private fun MonthGrid(month: MonthOverviewUi?, onEvent: (InsightsEvent) -> Unit) {
    if (month == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                onClick = { onEvent(InsightsEvent.PreviousMonth) },
                enabled = month.canGoEarlier,
                modifier = Modifier.testTag("previous-month-button"),
            ) { Text("Previous") }
            Text(month.month.toString(), style = MaterialTheme.typography.titleMedium)
            TextButton(
                onClick = { onEvent(InsightsEvent.NextMonth) },
                enabled = month.canGoLater,
                modifier = Modifier.testTag("next-month-button"),
            ) { Text("Next") }
        }

        Spacer(Modifier.width(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().testTag("month-grid"),
        ) {
            items(month.days) { day -> MonthDayCell(day) }
        }
    }
}

@Composable
private fun MonthDayCell(day: DayCellUi) {
    val stateSuffix = when (day.state) {
        DayCellState.OUTSIDE_RECORD -> "outside-record"
        DayCellState.NOT_YET_ELAPSED -> "not-yet-elapsed"
        DayCellState.NOTHING_RECORDED -> "nothing-recorded"
        DayCellState.PARTLY_RECORDED -> "partly-recorded"
        DayCellState.FULLY_RECORDED -> "fully-recorded"
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .testTag("month-day-${day.date}"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("month-day-${day.date}-$stateSuffix")
                .semantics { contentDescription = "${day.date}: $stateSuffix" }
                .then(
                    if (day.state == DayCellState.OUTSIDE_RECORD) {
                        Modifier.border(BorderStroke(1.dp, containerColorFor(DayCellState.FULLY_RECORDED)))
                    } else {
                        Modifier.background(containerColorFor(day.state))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.dayLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
