package com.giraffe.presentation.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.giraffe.presentation.dashboard.DashboardScreen
import com.giraffe.presentation.dashboard.DashboardViewModel
import com.giraffe.presentation.stats.StatsScreen
import com.giraffe.presentation.stats.StatsViewModel
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("Today", Icons.Default.Home)
    data object Stats : Screen("Stats", Icons.Default.Star)
}

@Composable
fun MizanNavHost() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen is Screen.Dashboard,
                    onClick = { currentScreen = Screen.Dashboard },
                    icon = { Icon(Screen.Dashboard.icon, contentDescription = "Today") },
                    label = { Text(Screen.Dashboard.label) }
                )
                NavigationBarItem(
                    selected = currentScreen is Screen.Stats,
                    onClick = { currentScreen = Screen.Stats },
                    icon = { Icon(Screen.Stats.icon, contentDescription = "Stats") },
                    label = { Text(Screen.Stats.label) }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            is Screen.Dashboard -> {
                val viewModel = koinViewModel<DashboardViewModel>()
                DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is Screen.Stats -> {
                val viewModel = koinViewModel<StatsViewModel>()
                StatsScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
