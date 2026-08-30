package com.giraffe.mizanapp.leaderboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** FR-003: the leave control lives in the same file as the opt-in, with no retention plea. */
@RunWith(AndroidJUnit4::class)
class LeaveControlTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun confirming_states_the_forward_backward_asymmetry_with_no_retention_plea() {
        var left = false
        compose.setContent { LeaveControl(onLeave = { left = true }) }

        compose.onNodeWithTag("leaderboard-leave").performClick()

        compose.onNodeWithText("period running now", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("already finished stay", substring = true, ignoreCase = true).assertExists()

        FORBIDDEN_WORDS.forEach { word ->
            compose.onNodeWithText(word, substring = true, ignoreCase = true).assertDoesNotExist()
        }

        compose.onNodeWithTag("leaderboard-leave-confirm").performClick()
        assertTrue(left)
    }

    private companion object {
        // FR-003's own forbidden trio, plus the full Conventions §6 list (CLAUDE.md, Principle IX).
        val FORBIDDEN_WORDS = listOf(
            "sure", "lose", "position",
            "failed", "failure", "error", "lost", "missing", "problem", "wrong",
            "you didn't", "you haven't", "retry now", "behind", "beat", "beaten",
            "overtake", "climb", "drop", "fell", "only", "just",
        )
    }
}
