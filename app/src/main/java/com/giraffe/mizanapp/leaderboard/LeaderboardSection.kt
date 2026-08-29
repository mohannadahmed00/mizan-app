package com.giraffe.mizanapp.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.leaderboard.OwnRankState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.leaderboard.Ranking
import com.giraffe.mizanapp.domain.leaderboard.RankingState
import java.time.Instant
import java.time.ZoneId

@Composable
fun LeaderboardSection(
    state: LeaderboardUiState,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onPeriodSelected: (PeriodKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.visibility) {
        Visibility.Hidden -> Unit
        Visibility.Invitation -> OptInPanel(onJoin = onJoin, modifier = modifier)
        Visibility.Participating -> Column(
            modifier = modifier.fillMaxWidth().testTag("leaderboard-ranking"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Regional standings", style = MaterialTheme.typography.titleLarge)
            state.regionLabel?.let { Text(it, modifier = Modifier.testTag("leaderboard-region")) }
            PeriodSelector(selected = state.selectedPeriod, onSelect = onPeriodSelected)
            OwnRankRow(state.ownRank)
            LeaveControl(onLeave = onLeave)
            when (val ranking = state.ranking) {
                RankingState.Unavailable -> Text("Standings aren't available right now")
                is RankingState.Cached -> {
                    CacheAgeLabel(ranking.ranking.retrievedAt, ranking.ranking.region.zone)
                    RankingRows(ranking.ranking)
                }
                is RankingState.Live -> RankingRows(ranking.ranking)
            }
            HonorBoardPanel(kind = state.selectedPeriod, state = state.honorBoard)
        }
    }
}

/** FR-011, FR-026: three period options, no default beyond WEEKLY; the weekly label states its Saturday-to-Friday span. */
@Composable
private fun PeriodSelector(
    selected: PeriodKind,
    onSelect: (PeriodKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodKind.entries.forEach { kind -> PeriodOption(kind, selected, onSelect) }
        }
        Text(periodLabel(selected), modifier = Modifier.testTag("leaderboard-period-label"))
    }
}

@Composable
private fun PeriodOption(kind: PeriodKind, selected: PeriodKind, onSelect: (PeriodKind) -> Unit) {
    val tag = Modifier.testTag("leaderboard-period-${kind.name.lowercase()}")
    val label = kind.name.lowercase().replaceFirstChar(Char::uppercase)
    if (kind == selected) {
        Button(onClick = { onSelect(kind) }, modifier = tag) { Text(label) }
    } else {
        TextButton(onClick = { onSelect(kind) }, modifier = tag) { Text(label) }
    }
}

fun periodLabel(kind: PeriodKind): String = when (kind) {
    PeriodKind.DAILY -> "Today"
    PeriodKind.WEEKLY -> "This week, Saturday to Friday"
    PeriodKind.MONTHLY -> "This month"
}

/** FR-023, SC-009: the viewer's own row, reachable without scrolling through the page. */
@Composable
private fun OwnRankRow(ownRank: OwnRankState) {
    if (ownRank !is OwnRankState.Available) return
    Card(modifier = Modifier.fillMaxWidth().testTag("leaderboard-own-rank")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(ownRank.ownRank.entry.position.toString())
            Text("${ownRank.ownRank.entry.points} points")
        }
    }
}

/** FR-036: a cached page states its age rather than rendering silently as current. */
@Composable
private fun CacheAgeLabel(retrievedAt: Instant, zone: ZoneId) {
    val local = retrievedAt.atZone(zone).toLocalTime()
    Text(
        "As of %02d:%02d".format(local.hour, local.minute),
        modifier = Modifier.testTag("leaderboard-cache-age"),
    )
}

@Composable
private fun RankingRows(ranking: Ranking) {
    ranking.entries.forEach { entry ->
        val container = if (entry.isViewer) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        }
        Card(
            modifier = Modifier.fillMaxWidth().testTag("leaderboard-row"),
            colors = CardDefaults.cardColors(containerColor = container),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.position.toString())
                Text(entry.displayName)
                Text("${entry.points} points")
            }
        }
    }
}
