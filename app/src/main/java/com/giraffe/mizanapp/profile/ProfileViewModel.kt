package com.giraffe.mizanapp.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One immutable state, exposed as [StateFlow]. No mutable state leaves this
 * class. See `contracts/ui-state.md` for the shape every transition follows.
 *
 * [SignOut] reads the pending count before it acts (FR-007c), so this class
 * only needs to remember which [SignOutMode] a confirmation is for — never a
 * count it would have to keep in sync with the live one itself.
 */
class ProfileViewModel(
    private val accounts: AccountRepository,
    private val sync: SyncRepository,
    private val signOut: SignOut,
    private val updateDisplayName: UpdateDisplayName,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var pendingMode: SignOutMode? = null

    init {
        viewModelScope.launch {
            accounts.observeSession().collect { session ->
                if (session is AccountSession.SignedIn) {
                    _state.value = _state.value.copy(email = session.email, displayName = session.displayName)
                }
            }
        }
        viewModelScope.launch {
            sync.observeStatus().collect { status -> _state.value = _state.value.copy(syncStatus = status) }
        }
        viewModelScope.launch {
            sync.observePendingCount().collect { count -> _state.value = _state.value.copy(pendingCount = count) }
        }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.DisplayNameChanged -> _state.value = _state.value.copy(
                draftDisplayName = event.value,
                editingDisplayName = true,
            )
            ProfileEvent.SaveDisplayName -> saveDisplayName()
            ProfileEvent.ClearDisplayName -> clearDisplayName()
            ProfileEvent.SignOut -> requestPlainConfirmation()
            ProfileEvent.SignOutAndRemoveData -> requestRemovingConfirmation()
            ProfileEvent.ConfirmSignOut -> confirmSignOut()
            ProfileEvent.CancelSignOut -> {
                pendingMode = null
                _state.value = _state.value.copy(confirming = null)
            }
        }
    }

    private fun saveDisplayName() {
        val name = _state.value.draftDisplayName.trim().ifEmpty { null }
        _state.value = _state.value.copy(displayName = name, editingDisplayName = false)
        viewModelScope.launch { updateDisplayName(name) }
    }

    private fun clearDisplayName() {
        _state.value = _state.value.copy(displayName = null, draftDisplayName = "", editingDisplayName = false)
        viewModelScope.launch { updateDisplayName(null) }
    }

    private fun requestPlainConfirmation() {
        pendingMode = SignOutMode.KEEP_LOCAL_RECORDS
        _state.value = _state.value.copy(confirming = SignOutConfirmation.Plain(_state.value.pendingCount))
    }

    private fun requestRemovingConfirmation() {
        pendingMode = SignOutMode.REMOVE_LOCAL_RECORDS
        viewModelScope.launch {
            val counts = accounts.localRecordCounts()
            _state.value = _state.value.copy(
                confirming = SignOutConfirmation.Removing(
                    pendingCount = _state.value.pendingCount,
                    recordedDays = counts.recordedDays,
                    completions = counts.completionCount,
                ),
            )
        }
    }

    private fun confirmSignOut() {
        val mode = pendingMode ?: return
        pendingMode = null
        _state.value = _state.value.copy(confirming = null)
        viewModelScope.launch { signOut(mode) }
    }
}
