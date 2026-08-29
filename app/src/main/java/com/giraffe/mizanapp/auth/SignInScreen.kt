package com.giraffe.mizanapp.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.giraffe.mizanapp.domain.identity.CodeRejection
import com.giraffe.mizanapp.domain.identity.SignInStep
import com.giraffe.mizanapp.domain.repository.CodeConfirmation

/**
 * The sign-in screen, rendering [SignInUiState] and nothing else — every
 * transition is driven by [onEvent] per `contracts/ui-state.md`.
 *
 * No password field exists anywhere here (FR-002). `NeedsConnection` renders
 * as an ordinary statement in body text, never a dialog and never red
 * (Principle IX).
 */
@Composable
fun SignInScreen(state: SignInUiState, onEvent: (SignInEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.configured) {
            Text("Signing in isn't available in this build.")
            return@Column
        }

        val confirmation = state.replaceConfirmation
        if (confirmation != null) {
            ReplaceConfirmation(confirmation, onEvent)
            return@Column
        }

        when (val step = state.step) {
            is SignInStep.EnteringEmail -> EnteringEmailContent(step, onEvent)
            is SignInStep.RequestingCode -> Text("Sending a code to ${step.email}…")
            is SignInStep.AwaitingCode -> AwaitingCodeContent(step, state, onEvent)
            is SignInStep.Confirming -> Text("Checking the code…")
            is SignInStep.CodeNotAccepted -> CodeNotAcceptedContent(step, state, onEvent)
            is SignInStep.NeedsConnection -> NeedsConnectionContent(step)
        }
    }
}

@Composable
private fun EnteringEmailContent(step: SignInStep.EnteringEmail, onEvent: (SignInEvent) -> Unit) {
    Text("Sign in with your email")
    OutlinedTextField(
        value = step.email,
        onValueChange = { onEvent(SignInEvent.EmailChanged(it)) },
        label = { Text("Email") },
        isError = step.invalid,
        modifier = Modifier.fillMaxWidth().testTag("signin-email-field"),
    )
    if (step.invalid) {
        Text("That address wasn't accepted.")
    }
    Button(
        onClick = { onEvent(SignInEvent.SubmitEmail) },
        modifier = Modifier.testTag("signin-submit-email"),
    ) {
        Text("Continue")
    }
}

@Composable
private fun AwaitingCodeContent(step: SignInStep.AwaitingCode, state: SignInUiState, onEvent: (SignInEvent) -> Unit) {
    Text("A code was sent to ${step.email}")
    OutlinedTextField(
        value = state.enteredCode,
        onValueChange = { onEvent(SignInEvent.CodeChanged(it)) },
        label = { Text("Code") },
        modifier = Modifier.fillMaxWidth().testTag("signin-code-field"),
    )
    Button(
        onClick = { onEvent(SignInEvent.SubmitCode) },
        modifier = Modifier.testTag("signin-submit-code"),
    ) {
        Text("Confirm")
    }
    TextButton(
        onClick = { onEvent(SignInEvent.ResendCode) },
        enabled = state.resendEnabled,
        modifier = Modifier.testTag("signin-resend"),
    ) {
        Text(if (state.resendEnabled) "Resend code" else "Resend available shortly")
    }
    TextButton(onClick = { onEvent(SignInEvent.UseDifferentAddress) }) {
        Text("Use a different address")
    }
}

@Composable
private fun CodeNotAcceptedContent(step: SignInStep.CodeNotAccepted, state: SignInUiState, onEvent: (SignInEvent) -> Unit) {
    val message = when (step.reason) {
        CodeRejection.EXPIRED -> "That code has expired. Request a new one."
        CodeRejection.INCORRECT -> "That code didn't match. Try entering it again."
    }
    Text(step.email)
    Text(message)
    OutlinedTextField(
        value = state.enteredCode,
        onValueChange = { onEvent(SignInEvent.CodeChanged(it)) },
        label = { Text("Code") },
        modifier = Modifier.fillMaxWidth().testTag("signin-code-field"),
    )
    Button(
        onClick = { onEvent(SignInEvent.SubmitCode) },
        modifier = Modifier.testTag("signin-submit-code"),
    ) {
        Text("Confirm")
    }
    TextButton(
        onClick = { onEvent(SignInEvent.ResendCode) },
        enabled = state.resendEnabled,
        modifier = Modifier.testTag("signin-resend"),
    ) {
        Text(if (state.resendEnabled) "Resend code" else "Resend available shortly")
    }
    TextButton(onClick = { onEvent(SignInEvent.UseDifferentAddress) }) {
        Text("Use a different address")
    }
}

@Composable
private fun NeedsConnectionContent(step: SignInStep.NeedsConnection) {
    Text(step.email)
    Text("Signing in needs a connection. The rest of the app still works offline.")
}

@Composable
private fun ReplaceConfirmation(confirmation: CodeConfirmation.WouldReplaceLocalRecords, onEvent: (SignInEvent) -> Unit) {
    Text("This device currently holds ${confirmation.currentEmail}'s records.")
    Text(
        "Signing in as a different account moves this device to that account: " +
            "${confirmation.recordedDays} recorded days and ${confirmation.completionCount} completions " +
            "(${confirmation.unsyncedCount} not yet backed up) will be removed from this device only — " +
            "${confirmation.currentEmail}'s account keeps everything.",
    )
    Button(onClick = { onEvent(SignInEvent.ConfirmReplaceLocalRecords) }) {
        Text("Continue with this account")
    }
    TextButton(onClick = { onEvent(SignInEvent.Dismiss) }) {
        Text("Cancel")
    }
}
