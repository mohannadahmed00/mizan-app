package com.giraffe.mizanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.giraffe.mizanapp.today.TodayScreen
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.ui.theme.MizanAppTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MizanAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TodayRoute(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun TodayRoute(modifier: Modifier = Modifier) {
    val viewModel: TodayViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Returning to the app after local midnight moves the screen to the new
    // date (FR-023). The ViewModel decides whether anything changed; this only
    // tells it to look.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshForCurrentDate()
        }
    }

    TodayScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
