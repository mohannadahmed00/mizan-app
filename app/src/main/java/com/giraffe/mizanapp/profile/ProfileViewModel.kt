package com.giraffe.mizanapp.profile

import androidx.lifecycle.ViewModel
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class. See `contracts/ui-state.md` for the shape every transition follows.
 */
class ProfileViewModel(
    private val accounts: AccountRepository,
    private val sync: SyncRepository,
    private val signOut: SignOut,
    private val updateDisplayName: UpdateDisplayName,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun onEvent(event: ProfileEvent) {
        TODO("T127")
    }
}
