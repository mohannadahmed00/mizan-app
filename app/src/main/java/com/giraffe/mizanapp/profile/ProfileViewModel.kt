package com.giraffe.mizanapp.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giraffe.mizanapp.data.notification.NotificationPreferencesStore
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationPreferences
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.notification.QuietHours
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import com.giraffe.mizanapp.notifications.NotificationSettings
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
    private val boundaryStatus: BoundaryStatus,
    private val notificationPreferencesStore: NotificationPreferencesStore,
    private val notificationScheduler: NotificationScheduler,
    private val notificationPresenter: NotificationPresenter,
    private val time: TimeProvider,
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
        viewModelScope.launch {
            boundaryStatus.observe().collect { boundaryState ->
                _state.value = _state.value.copy(locationSettings = locationSettingsFor(boundaryState))
            }
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
            ProfileEvent.EnableLocation -> viewModelScope.launch { boundaryStatus.requestLocation() }
            ProfileEvent.EraseLocation -> _state.value = _state.value.copy(confirmingEraseLocation = true)
            ProfileEvent.ConfirmEraseLocation -> {
                _state.value = _state.value.copy(confirmingEraseLocation = false)
                viewModelScope.launch { boundaryStatus.eraseLocation() }
            }
            ProfileEvent.CancelEraseLocation -> _state.value = _state.value.copy(confirmingEraseLocation = false)
            is ProfileEvent.NotificationSettingsChanged -> updateNotificationState(event.event)
        }
    }

    /**
     * Every event that changes preferences persists and then withdraws or cancels in the same
     * operation (FR-005): a switch flipped off must stop being true the instant it flips, not at
     * the worker's next natural wake. A category still on, or the master silence turning off, is
     * safe to leave to the next fire — `evaluateAnchor`'s `CATEGORY_OFF`/`ALL_SILENCED` checks
     * already guard it, so nothing stale can be shown in the meantime.
     */
    private fun updateNotificationState(event: NotificationSettingsEvent) {
        if (event is NotificationSettingsEvent.RequestPermission || event is NotificationSettingsEvent.OpenSystemSettings) return

        val current = _state.value.notifications
        val next = when (event) {
            is NotificationSettingsEvent.SetCategory -> when (event.category) {
                NotificationCategory.PRAYER_WINDOW -> current.copy(prayerWindowEnabled = event.enabled)
                NotificationCategory.STREAK_AT_RISK -> current.copy(streakAtRiskEnabled = event.enabled)
                NotificationCategory.WEEKLY_SUMMARY -> current.copy(weeklySummaryEnabled = event.enabled)
            }
            is NotificationSettingsEvent.SetAllSilenced -> current.copy(allSilenced = event.silenced)
            is NotificationSettingsEvent.SetQuietHours -> current.copy(quietHours = QuietHours(event.start, event.end))
            NotificationSettingsEvent.ClearQuietHours -> current.copy(quietHours = null)
            else -> current
        }
        _state.value = _state.value.copy(notifications = next)

        viewModelScope.launch {
            notificationPreferencesStore.save(next.toDomain())
            if (next.allSilenced) {
                notificationScheduler.cancelAll()
            } else if (event is NotificationSettingsEvent.SetCategory && !event.enabled) {
                withdrawTodayKeysFor(event.category)
            }
        }
    }

    private fun NotificationSettings.toDomain() = NotificationPreferences(
        enabled = buildSet {
            if (prayerWindowEnabled) add(NotificationCategory.PRAYER_WINDOW)
            if (streakAtRiskEnabled) add(NotificationCategory.STREAK_AT_RISK)
            if (weeklySummaryEnabled) add(NotificationCategory.WEEKLY_SUMMARY)
        },
        allSilenced = allSilenced,
        quietHours = quietHours,
    )

    private suspend fun withdrawTodayKeysFor(category: NotificationCategory) {
        val today = time.today()
        when (category) {
            NotificationCategory.PRAYER_WINDOW ->
                listOf("fajr", "dhuhr", "asr", "maghrib", "isha").forEach { notificationPresenter.withdraw("PRAYER:$today:$it") }
            NotificationCategory.STREAK_AT_RISK -> notificationPresenter.withdraw("STREAK:$today")
            NotificationCategory.WEEKLY_SUMMARY -> notificationPresenter.withdraw(
                "WEEK:${com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(today).key.value}",
            )
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
