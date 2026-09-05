package com.giraffe.mizanapp.notifications

import com.giraffe.mizanapp.data.db.daos.NotificationDao
import com.giraffe.mizanapp.data.db.entities.NotificationDeliveryEntity
import com.giraffe.mizanapp.data.db.entities.NotificationPreferencesEntity
import com.giraffe.mizanapp.data.notification.NotificationPreferencesStore
import com.giraffe.mizanapp.domain.identity.AccountSession
import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.CodeRequest
import com.giraffe.mizanapp.domain.repository.LocalRecordCounts
import com.giraffe.mizanapp.domain.repository.SyncRepository
import com.giraffe.mizanapp.domain.sync.SyncStatus
import com.giraffe.mizanapp.domain.time.TimeProvider
import com.giraffe.mizanapp.domain.usecase.SignOut
import com.giraffe.mizanapp.domain.usecase.UpdateDisplayName
import com.giraffe.mizanapp.profile.NotificationSettingsEvent
import com.giraffe.mizanapp.profile.ProfileEvent
import com.giraffe.mizanapp.profile.ProfileViewModel
import com.giraffe.mizanapp.today.FakeBoundaryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelNotificationsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class ScriptedAccountRepository : AccountRepository {
        val sessionFlow = MutableStateFlow<AccountSession>(AccountSession.SignedIn("u-1", "user@example.test"))
        override fun observeSession(): Flow<AccountSession> = sessionFlow
        override suspend fun requestCode(email: String): CodeRequest = error("not used")
        override suspend fun confirmCode(email: String, code: String, replaceLocalRecords: Boolean): CodeConfirmation = error("not used")
        override suspend fun signOut(mode: SignOutMode) = Unit
        override suspend fun updateDisplayName(name: String?) = Unit
        override suspend fun localRecordCounts(): LocalRecordCounts = LocalRecordCounts(0, 0)
    }

    private class ScriptedSyncRepository : SyncRepository {
        override fun observeStatus(): Flow<SyncStatus> = MutableStateFlow(SyncStatus.UpToDate)
        override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)
        override fun syncNow() = Unit
    }

    private class FakeNotificationDao : NotificationDao {
        var row: NotificationPreferencesEntity? = null
        val saved = mutableListOf<NotificationPreferencesEntity>()
        override suspend fun preferences(): NotificationPreferencesEntity? = row
        override fun observePreferences(): Flow<NotificationPreferencesEntity?> = MutableStateFlow(row)
        override suspend fun upsertPreferences(e: NotificationPreferencesEntity) { row = e; saved += e }
        override suspend fun deliveries(): List<NotificationDeliveryEntity> = emptyList()
        override suspend fun delivery(anchorKey: String): NotificationDeliveryEntity? = null
        override suspend fun upsertDelivery(e: NotificationDeliveryEntity) = Unit
        override suspend fun pruneBefore(before: Long) = Unit
    }

    private class FakeScheduler : NotificationScheduler {
        var cancelAllCalls = 0
        val replaceAllCalls = mutableListOf<List<NotificationAnchor>>()
        override suspend fun replaceAll(anchors: List<NotificationAnchor>) { replaceAllCalls += anchors }
        override suspend fun cancelAll() { cancelAllCalls++ }
        override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
        override suspend fun scheduleRefresh(at: Instant) = Unit
        override suspend fun scheduleAt(anchorKey: String, at: Instant) = Unit
    }

    private class FakePresenter : NotificationPresenter {
        val withdrawn = mutableListOf<String>()
        override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) = Unit
        override suspend fun withdraw(anchorKey: String) { withdrawn += anchorKey }
        override fun hasPermission(): Boolean = true
    }

    private class FixedTime(private val date: LocalDate) : TimeProvider {
        override fun now(): Instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
        override fun today(): LocalDate = date
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private lateinit var scheduler: FakeScheduler
    private lateinit var presenter: FakePresenter
    private lateinit var dao: FakeNotificationDao

    @Before fun freshFakes() {
        scheduler = FakeScheduler()
        presenter = FakePresenter()
        dao = FakeNotificationDao()
    }

    private fun buildViewModel(): ProfileViewModel {
        val accounts = ScriptedAccountRepository()
        val sync = ScriptedSyncRepository()
        return ProfileViewModel(
            accounts,
            sync,
            SignOut(accounts, sync),
            UpdateDisplayName(accounts),
            FakeBoundaryStatus(),
            NotificationPreferencesStore(dao),
            scheduler,
            presenter,
            FixedTime(LocalDate.of(2026, 9, 5)),
        )
    }

    @Test fun turningACategoryOffPersistsAndWithdrawsTodaysNotification() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.STREAK_AT_RISK, false)))
        advanceUntilIdle()

        assertTrue(presenter.withdrawn.contains("STREAK:2026-09-05"))
        assertTrue(dao.saved.isNotEmpty())
        assertEquals(false, dao.saved.last().streakAtRiskEnabled)
    }

    @Test fun turningPrayerCategoryOffWithdrawsAllFiveSectionKeys() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.PRAYER_WINDOW, false)))
        advanceUntilIdle()

        listOf("fajr", "dhuhr", "asr", "maghrib", "isha").forEach { section ->
            assertTrue(presenter.withdrawn.contains("PRAYER:2026-09-05:$section"))
        }
    }

    @Test fun setAllSilencedTrueCallsCancelAllAndDoesNotClearCategoryBooleans() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetAllSilenced(true)))
        advanceUntilIdle()

        assertEquals(1, scheduler.cancelAllCalls)
        assertEquals(true, viewModel.state.value.notifications.weeklySummaryEnabled)
        assertEquals(true, dao.saved.last().weeklySummaryEnabled)
        assertEquals(true, dao.saved.last().allSilenced)
    }

    @Test fun setAllSilencedFalseRestoresExactlyThePreviousCategories() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetCategory(NotificationCategory.PRAYER_WINDOW, true)))
        advanceUntilIdle()
        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetAllSilenced(true)))
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetAllSilenced(false)))
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.notifications.prayerWindowEnabled)
        assertEquals(true, viewModel.state.value.notifications.weeklySummaryEnabled)
        assertEquals(false, viewModel.state.value.notifications.allSilenced)
    }

    @Test fun setQuietHoursPersists() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onEvent(ProfileEvent.NotificationSettingsChanged(NotificationSettingsEvent.SetQuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0))))
        advanceUntilIdle()

        assertEquals("22:00", dao.saved.last().quietStart)
        assertEquals("06:00", dao.saved.last().quietEnd)
    }
}
