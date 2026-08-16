package com.giraffe.mizanapp.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Feeds [SyncStatusBar] on every screen that hosts it, from one shared read of [SyncRepository]. */
class SyncStatusViewModel(sync: SyncRepository) : ViewModel() {
    val status: StateFlow<SyncStatus> = sync.observeStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.NotSignedIn)
}
