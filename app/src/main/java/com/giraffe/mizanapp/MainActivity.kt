package com.giraffe.mizanapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.giraffe.mizanapp.daysummary.DaySummaryScreen
import com.giraffe.mizanapp.daysummary.DaySummaryViewModel
import com.giraffe.mizanapp.today.TodayScreen
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.ui.theme.MizanAppTheme
import com.giraffe.mizanapp.week.WeekEvent
import com.giraffe.mizanapp.week.WeekScreen
import com.giraffe.mizanapp.week.WeekViewModel
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The destination currently shown. Held here rather than via a navigation
 * library — two screens and one date parameter is cheaper hand-rolled than a
 * dependency, and Principle VIII forbids the abstraction until a third
 * destination with its own back stack (the tab shell) makes it worthwhile
 * (research.md R3).
 */
sealed interface Destination {
    data object Today : Destination
    data object Week : Destination
    data class DaySummary(val date: LocalDate) : Destination
}

/**
 * `rememberSaveable`'s default `Saver` only handles types it can place
 * directly in a `Bundle` — a sealed interface holding a `LocalDate` is not
 * one of them, and using the default silently crashes on first composition.
 * This encodes [Destination] as the single `String` the default saver
 * already knows how to store.
 */
private val DestinationSaver = Saver<Destination, String>(
    save = { destination ->
        when (destination) {
            Destination.Today -> "TODAY"
            Destination.Week -> "WEEK"
            is Destination.DaySummary -> "DAY:${destination.date}"
        }
    },
    restore = { encoded ->
        when {
            encoded == "TODAY" -> Destination.Today
            encoded == "WEEK" -> Destination.Week
            encoded.startsWith("DAY:") -> Destination.DaySummary(LocalDate.parse(encoded.removePrefix("DAY:")))
            else -> Destination.Today
        }
    },
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MizanAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoute(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun AppRoute(modifier: Modifier = Modifier) {
    var destination by rememberSaveable(stateSaver = DestinationSaver) { mutableStateOf<Destination>(Destination.Today) }

    when (val current = destination) {
        Destination.Today -> TodayRoute(
            onOpenWeek = { destination = Destination.Week },
            modifier = modifier,
        )
        Destination.Week -> {
            BackHandler { destination = Destination.Today }
            WeekRoute(
                onOpenDay = { date -> destination = Destination.DaySummary(date) },
                modifier = modifier,
            )
        }
        is Destination.DaySummary -> {
            BackHandler { destination = Destination.Week }
            DaySummaryRoute(date = current.date, modifier = modifier)
        }
    }
}

@Composable
private fun TodayRoute(onOpenWeek: () -> Unit, modifier: Modifier = Modifier) {
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

    TodayScreen(state = state, onEvent = viewModel::onEvent, onOpenWeek = onOpenWeek, modifier = modifier)
}

@Composable
private fun WeekRoute(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WeekViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Crossing local midnight into a new week moves the sheet forward
    // (FR-020), mirroring TodayRoute's own rollover check.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshForCurrentDate()
        }
    }

    WeekScreen(
        state = state,
        onEvent = { event ->
            if (event is WeekEvent.OpenDay) onOpenDay(event.date) else viewModel.onEvent(event)
        },
        modifier = modifier,
    )
}

@Composable
private fun DaySummaryRoute(date: LocalDate, modifier: Modifier = Modifier) {
    val viewModel: DaySummaryViewModel = koinViewModel { parametersOf(date) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    DaySummaryScreen(state = state, modifier = modifier)
}
