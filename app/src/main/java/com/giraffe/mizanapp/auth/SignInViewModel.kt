package com.giraffe.mizanapp.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.identity.SignInStep
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.ConfirmSignInCode
import com.giraffe.mizanapp.domain.usecase.RequestSignInCode
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class. See `contracts/ui-state.md` and `contracts/auth.md` for the state
 * machine every transition here follows.
 */
class SignInViewModel(
    private val requestCode: RequestSignInCode,
    private val confirmCode: ConfirmSignInCode,
    private val time: TimeProvider,
    configured: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState(configured = configured))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            is SignInEvent.EmailChanged -> _state.value = _state.value.copy(step = SignInStep.EnteringEmail(event.value))
            SignInEvent.SubmitEmail -> submitEmail(_state.value.step.email)
            is SignInEvent.CodeChanged -> _state.value = _state.value.copy(enteredCode = event.value)
            SignInEvent.SubmitCode -> submitCode()
            SignInEvent.ResendCode -> resend()
            SignInEvent.UseDifferentAddress -> _state.value = _state.value.copy(
                step = SignInStep.EnteringEmail(_state.value.step.email),
                enteredCode = "",
            )
            SignInEvent.ConfirmReplaceLocalRecords -> confirmReplace()
            SignInEvent.Dismiss -> _state.value = _state.value.copy(replaceConfirmation = null)
        }
    }

    private fun submitEmail(email: String) {
        _state.value = _state.value.copy(step = SignInStep.RequestingCode(email), resendEnabled = false)
        viewModelScope.launch {
            when (val result = requestCode(email)) {
                is CodeRequest.Sent -> {
                    _state.value = _state.value.copy(
                        step = SignInStep.AwaitingCode(email, result.resendAvailableAt),
                        resendEnabled = false,
                    )
                    scheduleResendEnable(result.resendAvailableAt)
                }
                CodeRequest.NeedsConnection -> _state.value = _state.value.copy(step = SignInStep.NeedsConnection(email))
                is CodeRequest.AddressNotAccepted -> _state.value = _state.value.copy(
                    step = SignInStep.EnteringEmail(email, invalid = true),
                )
            }
        }
    }

    private fun scheduleResendEnable(availableAt: Instant) {
        viewModelScope.launch {
            val wait = Duration.between(time.now(), availableAt)
            if (!wait.isNegative) delay(wait.toMillis())
            if (_state.value.step is SignInStep.AwaitingCode) {
                _state.value = _state.value.copy(resendEnabled = true)
            }
        }
    }

    private fun resend() {
        val step = _state.value.step
        if (step !is SignInStep.AwaitingCode) return
        if (time.now().isBefore(step.resendAvailableAt)) return
        submitEmail(step.email)
    }

    private fun submitCode() {
        val email = _state.value.step.email
        val code = _state.value.enteredCode
        _state.value = _state.value.copy(step = SignInStep.Confirming(email, code))
        viewModelScope.launch {
            when (val result = confirmCode(email, code)) {
                is CodeConfirmation.SignedIn -> Unit
                is CodeConfirmation.NotAccepted -> _state.value = _state.value.copy(
                    step = SignInStep.CodeNotAccepted(email, result.reason),
                )
                CodeConfirmation.NeedsConnection -> _state.value = _state.value.copy(step = SignInStep.NeedsConnection(email))
                is CodeConfirmation.WouldReplaceLocalRecords -> _state.value = _state.value.copy(replaceConfirmation = result)
            }
        }
    }

    private fun confirmReplace() {
        _state.value.replaceConfirmation ?: return
        val email = _state.value.step.email
        val code = _state.value.enteredCode
        _state.value = _state.value.copy(replaceConfirmation = null)
        viewModelScope.launch { confirmCode(email, code, replaceLocalRecords = true) }
    }
}
