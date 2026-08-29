package com.giraffe.mizanapp.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun OptInPanel(
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag("leaderboard-invitation")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Join the leaderboard")
            Text(
                "Other people in your region will see your name and your points for the current period, " +
                    "including what you have already recorded in it.",
            )
            Text(
                "You can leave at any time. Leaving takes you out of the period running now and any that " +
                    "follow — periods that have already finished stay as they are.",
            )
            Text(
                "Periods close when they end, so anything you record offline and sync afterwards counts in " +
                    "your own record but not toward that period's standings.",
            )
            Button(
                onClick = onJoin,
                modifier = Modifier.testTag("leaderboard-join"),
            ) {
                Text("Join")
            }
        }
    }
}
