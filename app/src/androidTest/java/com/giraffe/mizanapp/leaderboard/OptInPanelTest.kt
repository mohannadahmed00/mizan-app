package com.giraffe.mizanapp.leaderboard

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OptInPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun all_irreversible_visibility_terms_appear_before_joining_is_available() {
        compose.setContent { OptInPanel(onJoin = {}) }

        compose.onNodeWithText("name", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("points", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("your region", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("already finished stay", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("already recorded", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("sync afterwards", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithTag("leaderboard-join").assertIsEnabled()
    }
}
