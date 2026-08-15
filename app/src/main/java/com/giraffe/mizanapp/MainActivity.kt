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
import com.giraffe.mizanapp.history.HistoryEvent
import com.giraffe.mizanapp.history.HistoryScreen
import com.giraffe.mizanapp.history.HistoryViewModel
import com.giraffe.mizanapp.today.TodayScreen
import com.giraffe.mizanapp.today.TodayViewModel
import com.giraffe.mizanapp.ui.theme.MizanAppTheme
import com.giraffe.mizanapp.week.WeekEvent
import com.giraffe.mizanapp.week.WeekScreen
import com.giraffe.mizanapp.week.WeekViewModel
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The destination currently shown, held as a back stack in
 * `rememberSaveable` — a navigation library was deferred in `002` "until a
 * third destination with its own back stack ... makes it worthwhile"
 * (research.md R3 there); `005` FR-015 is that moment, since a past day must
 * return to whichever list opened it (history or the weekly sheet), which a
 * single field cannot represent.
 */
sealed interface Destination {
    data object Today : Destination
    data object Week : Destination
    data object History : Destination
    data class DaySummary(val date: LocalDate) : Destination
}

/**
 * `rememberSaveable`'s default `Saver` only handles types it can place
 * directly in a `Bundle` — a list of a sealed interface holding a `LocalDate`
 * is not one of them, and using the default silently crashes on first
 * composition. This encodes the whole [Destination] stack as the single
 * `String` the default saver already knows how to store, joined on `"|"` —
 * a character that cannot appear in an ISO date.
 */
private fun encode(destination: Destination): String = when (destination) {
    Destination.Today -> "TODAY"
    Destination.Week -> "WEEK"
    Destination.History -> "HISTORY"
    is Destination.DaySummary -> "DAY:${destination.date}"
}

private fun decode(encoded: String): Destination = when {
    encoded == "TODAY" -> Destination.Today
    encoded == "WEEK" -> Destination.Week
    encoded == "HISTORY" -> Destination.History
    encoded.startsWith("DAY:") -> Destination.DaySummary(LocalDate.parse(encoded.removePrefix("DAY:")))
    else -> Destination.Today
}

private val StackSaver = Saver<List<Destination>, String>(
    save = { stack -> stack.joinToString("|") { encode(it) } },
    restore = { encoded ->
        encoded.split("|").filter { it.isNotEmpty() }.map { decode(it) }
            .ifEmpty { listOf(Destination.Today) }
    },
)

/**
 * Where a date opens (FR-015a). The current date always opens the recording
 * surface — there is exactly one surface in the app that can record
 * (FR-023) — every other date opens the read-only detail.
 */
fun destinationForDate(date: LocalDate, today: LocalDate): Destination =
    if (date == today) Destination.Today else Destination.DaySummary(date)

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
    var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(listOf<Destination>(Destination.Today)) }
    val time: TimeProvider = koinInject()

    fun push(destination: Destination) {
        stack = stack + destination
    }
    fun openDate(date: LocalDate) {
        push(destinationForDate(date, time.today()))
    }

    // One handler for the whole stack: at the root, back exits the app as normal.
    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    when (val current = stack.last()) {
        Destination.Today -> TodayRoute(
            onOpenWeek = { push(Destination.Week) },
            modifier = modifier,
        )
        Destination.Week -> WeekRoute(
            onOpenDay = ::openDate,
            onOpenHistory = { push(Destination.History) },
            modifier = modifier,
        )
        Destination.History -> HistoryRoute(
            onOpenDay = ::openDate,
            modifier = modifier,
        )
        is Destination.DaySummary -> DaySummaryRoute(date = current.date, modifier = modifier)
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
private fun WeekRoute(onOpenDay: (LocalDate) -> Unit, onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
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
            when (event) {
                is WeekEvent.OpenDay -> onOpenDay(event.date)
                WeekEvent.OpenHistory -> onOpenHistory()
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun HistoryRoute(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    HistoryScreen(
        state = state,
        onEvent = { event ->
            if (event is HistoryEvent.OpenDay) onOpenDay(event.date) else viewModel.onEvent(event)
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
