package com.giraffe.mizanapp.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.leaderboard.HonorBoardState
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind

/**
 * FR-027a: rendered for WEEKLY and MONTHLY only — for DAILY, absent entirely,
 * not empty and not disabled. Renders qualifying members unordered, with no
 * points and no position, and says nothing about a non-qualifying viewer.
 */
@Composable
fun HonorBoardPanel(
    kind: PeriodKind,
    state: HonorBoardState,
    modifier: Modifier = Modifier,
) {
    if (kind == PeriodKind.DAILY) return
    if (state !is HonorBoardState.Available) return

    Column(
        modifier = modifier.fillMaxWidth().testTag("honor-board"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Honor Board", style = MaterialTheme.typography.titleLarge)
        state.honorBoard.members.forEach { member ->
            Card(modifier = Modifier.fillMaxWidth().testTag("honor-board-member")) {
                Text(member.displayName, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
