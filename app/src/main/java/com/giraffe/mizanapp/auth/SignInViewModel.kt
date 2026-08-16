package com.giraffe.mizanapp.auth

import androidx.lifecycle.ViewModel
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.ConfirmSignInCode
import com.giraffe.mizanapp.domain.usecase.RequestSignInCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        TODO("T068")
    }
}
