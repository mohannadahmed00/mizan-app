package com.giraffe.mizanapp

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * research R7: `mizan.destination` seeds the back stack once, from a notification tap, and
 * a configuration change must never re-navigate on top of whatever the person has since done.
 */
@RunWith(AndroidJUnit4::class)
class NotificationDeepLinkTest {

    @get:Rule val compose = createEmptyComposeRule()

    private fun launch(extra: String?): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extra?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) }
        }
        return ActivityScenario.launch(intent)
    }

    @Test fun explicitWeekKeyOpensThatWeekNotTheMostRecent() {
        val scenario = launch("WEEKLYSUMMARY:2026-08-29")
        compose.waitForIdle()
        compose.onNodeWithText("2026-08-29", substring = true).assertExists()
        scenario.close()
    }

    @Test fun bareWeeklySummaryOpensTheMostRecentClosedWeek() {
        val scenario = launch("WEEKLYSUMMARY")
        compose.waitForIdle()
        // Whatever week is most recent, this must not be the Today screen.
        compose.onNodeWithText("Today", substring = true).assertDoesNotExist()
        scenario.close()
    }

    @Test fun rotatingTheDeviceDoesNotReNavigate() {
        val scenario = launch("WEEKLYSUMMARY:2026-08-29")
        compose.waitForIdle()
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("2026-08-29", substring = true).assertExists()
        scenario.close()
    }

    @Test fun launchingWithNoExtraOpensToday() {
        val scenario = launch(null)
        compose.waitForIdle()
        compose.onNodeWithTag("streak-element").assertExists()
        scenario.close()
    }

    @Test fun sectionExtraOpensTodayOnThatBlock() {
        val scenario = launch("TODAY:asr")
        compose.waitForIdle()
        compose.onNodeWithTag("section-asr").assertExists()
        scenario.close()
    }

    @Test fun streakReminderDestinationOpensToday() {
        val scenario = launch("TODAY")
        compose.waitForIdle()
        compose.onNodeWithTag("streak-element").assertExists()
        scenario.close()
    }
}
