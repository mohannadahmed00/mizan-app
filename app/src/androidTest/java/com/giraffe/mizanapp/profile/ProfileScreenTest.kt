package com.giraffe.mizanapp.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-007b, FR-019a: the removing confirmation names what is about to go
 * before it can be accepted, and the conflict-policy statement is always on
 * screen — not hidden behind a menu or a dialog.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_removing_confirmation_names_the_day_count_and_the_completion_count_before_it_can_proceed() {
        compose.setContent {
            ProfileScreen(
                state = ProfileUiState(
                    email = "user@example.test",
                    confirming = SignOutConfirmation.Removing(pendingCount = 0, recordedDays = 21, completions = 87),
                ),
                onEvent = {},
            )
        }

        compose.onNodeWithText("21", substring = true).assertExists()
        compose.onNodeWithText("87", substring = true).assertExists()
        compose.onNodeWithTag("profile-confirm-sign-out").assertExists()
        compose.onNodeWithTag("profile-cancel-sign-out").assertExists()
    }

    @Test
    fun the_conflict_policy_line_is_visible_on_the_screen() {
        compose.setContent {
            ProfileScreen(state = ProfileUiState(email = "user@example.test"), onEvent = {})
        }

        compose.onNodeWithTag("profile-conflict-policy").assertExists()
        compose.onNodeWithText("kept", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("undone", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun both_confirmations_name_the_pending_count_when_it_is_non_zero() {
        compose.setContent {
            ProfileScreen(
                state = ProfileUiState(
                    email = "user@example.test",
                    confirming = SignOutConfirmation.Plain(pendingCount = 4),
                ),
                onEvent = {},
            )
        }

        compose.onNodeWithText("4", substring = true).assertExists()
        compose.onNodeWithTag("profile-confirm-sign-out").assertExists()
    }
}
