package com.giraffe.mizanapp.auth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.identity.SignInStep
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val email = "user@example.test"

    @Test
    fun entering_email_state_renders_the_email_field() {
        compose.setContent {
            SignInScreen(state = SignInUiState(step = SignInStep.EnteringEmail("")), onEvent = {})
        }
        compose.onNodeWithTag("signin-email-field").assertExists()
    }

    @Test
    fun awaiting_code_state_renders_the_code_field_and_resend() {
        compose.setContent {
            SignInScreen(
                state = SignInUiState(
                    step = SignInStep.AwaitingCode(email, Instant.parse("2026-08-16T09:00:30Z")),
                ),
                onEvent = {},
            )
        }
        compose.onNodeWithTag("signin-code-field").assertExists()
        compose.onNodeWithTag("signin-resend").assertExists()
    }

    @Test
    fun code_not_accepted_state_renders_and_keeps_the_email() {
        compose.setContent {
            SignInScreen(
                state = SignInUiState(step = SignInStep.CodeNotAccepted(email, CodeRejection.EXPIRED)),
                onEvent = {},
            )
        }
        compose.onNodeWithText(email, substring = true).assertExists()
    }

    @Test
    fun needs_connection_state_renders_a_statement_and_the_app_stays_navigable() {
        compose.setContent {
            SignInScreen(state = SignInUiState(step = SignInStep.NeedsConnection(email)), onEvent = {})
        }
        compose.onNodeWithText("connection", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun email_field_keeps_its_value_across_a_failed_code_entry() {
        compose.setContent {
            SignInScreen(
                state = SignInUiState(step = SignInStep.CodeNotAccepted(email, CodeRejection.INCORRECT)),
                onEvent = {},
            )
        }
        compose.onNodeWithText(email, substring = true).assertExists()
    }

    @Test
    fun no_password_field_exists_anywhere_in_the_hierarchy() {
        compose.setContent {
            SignInScreen(state = SignInUiState(step = SignInStep.EnteringEmail("")), onEvent = {})
        }
        compose.onAllNodes(hasText("password", substring = true, ignoreCase = true))
            .assertCountEquals(0)
    }

    @Test
    fun no_forbidden_word_appears_in_any_node_text() {
        val forbidden = listOf(
            "failed", "failure", "error", "lost", "missing", "problem", "wrong",
            "you didn't", "you haven't", "retry now",
        )
        val states = listOf(
            SignInUiState(step = SignInStep.EnteringEmail("")),
            SignInUiState(step = SignInStep.AwaitingCode(email, Instant.parse("2026-08-16T09:00:30Z"))),
            SignInUiState(step = SignInStep.CodeNotAccepted(email, CodeRejection.EXPIRED)),
            SignInUiState(step = SignInStep.NeedsConnection(email)),
        )
        val current = mutableStateOf(states.first())
        compose.setContent { SignInScreen(state = current.value, onEvent = {}) }

        for (step in states) {
            compose.runOnIdle { current.value = step }
            for (word in forbidden) {
                compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
            }
        }
    }
}
