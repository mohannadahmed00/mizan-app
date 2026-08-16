package com.giraffe.mizanapp.auth

import com.giraffe.mizanapp.domain.identity.SignInStep
import com.giraffe.mizanapp.domain.repository.CodeConfirmation

/**
 * One immutable state for the sign-in screen, per `contracts/ui-state.md`.
 *
 * [step] carries the entered email through every transition (FR-002a).
 * [configured] false means this build has no Supabase configuration — the
 * screen renders one line and nothing else, and never crashes (FR-003).
 * [replaceConfirmation] is non-null only between a
 * [CodeConfirmation.WouldReplaceLocalRecords] outcome and the user's decision;
 * declining clears it and leaves the device untouched (FR-013a).
 */
data class SignInUiState(
    val step: SignInStep = SignInStep.EnteringEmail(""),
    val enteredCode: String = "",
    val resendEnabled: Boolean = false,
    val configured: Boolean = true,
    val replaceConfirmation: CodeConfirmation.WouldReplaceLocalRecords? = null,
)

sealed interface SignInEvent {
    data class EmailChanged(val value: String) : SignInEvent
    data object SubmitEmail : SignInEvent
    data class CodeChanged(val value: String) : SignInEvent
    data object SubmitCode : SignInEvent
    data object ResendCode : SignInEvent
    data object UseDifferentAddress : SignInEvent
    data object ConfirmReplaceLocalRecords : SignInEvent
    data object Dismiss : SignInEvent
}
