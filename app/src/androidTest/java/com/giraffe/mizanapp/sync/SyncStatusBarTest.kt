package com.giraffe.mizanapp.sync

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.sync.SyncStatus
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStatusBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun each_status_renders_its_expected_text() {
        val cases = listOf(
            SyncStatus.UpToDate to "Backed up",
            SyncStatus.Pending(5) to "5 changes waiting to be sent",
            SyncStatus.NotSyncing to "Not syncing right now",
            SyncStatus.LoadingEarlierDays(LocalDate.of(2026, 6, 1)) to "Still loading earlier days",
        )
        val current = mutableStateOf<SyncStatus>(cases.first().first)
        compose.setContent { SyncStatusBar(current.value) }

        for ((status, expected) in cases) {
            compose.runOnIdle { current.value = status }
            compose.onNodeWithText(expected).assertExists()
        }
    }

    @Test
    fun NotSignedIn_renders_nothing_at_all() {
        compose.setContent { SyncStatusBar(SyncStatus.NotSignedIn) }
        compose.onAllNodesWithTag("sync-status-bar").assertCountEquals(0)
    }

    @Test
    fun the_bar_is_not_clickable() {
        compose.setContent { SyncStatusBar(SyncStatus.Pending(2)) }
        compose.onNodeWithTag("sync-status-bar").assertHasNoClickAction()
    }

    @Test
    fun no_pixel_it_renders_is_red_orange_or_amber() {
        compose.setContent { SyncStatusBar(SyncStatus.Pending(2)) }

        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var reddish = false
        for (x in 0 until pixels.width step 4) {
            for (y in 0 until pixels.height step 4) {
                val pixel: Color = pixels[x, y]
                // A reddish/warning hue: red channel clearly dominant over both
                // green and blue.
                if (pixel.red > 0.5f && pixel.red > pixel.green + 0.15f && pixel.red > pixel.blue + 0.15f) {
                    reddish = true
                }
            }
        }
        assertFalse("SyncStatusBar must render no red/orange/amber pixel", reddish)
    }
}
