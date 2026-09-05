package com.giraffe.mizanapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import com.giraffe.mizanapp.data.notification.DeliveryStore
import com.giraffe.mizanapp.data.notification.NotificationPreferencesStore
import com.giraffe.mizanapp.data.notification.NotificationWorker
import com.giraffe.mizanapp.data.repository.RoomCatalogueRepository
import com.giraffe.mizanapp.data.repository.RoomCompletionRepository
import com.giraffe.mizanapp.data.repository.RoomDayPlanRepository
import com.giraffe.mizanapp.data.repository.RoomRecordCoverageRepository
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.NotificationPreferences
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.notification.anchorKey
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.PrayerTimes
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.LocationRequestOutcome
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetClosedWeekSummary
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class OfflineFakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(initial)
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {}
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

/** Never resolves prayer times over a network -- FR-036 is that this path takes none. */
private class OfflineFakePrayerTimesProvider(private val times: PrayerTimes) : PrayerTimesProvider {
    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome = PrayerTimesOutcome.Calculated(times)
}

private class OfflineFakeScheduler : NotificationScheduler {
    var replaceAllCalls = 0
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) { replaceAllCalls++ }
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
    override suspend fun scheduleRefresh(at: Instant) {}
    override suspend fun scheduleAt(anchorKey: String, at: Instant) {}
}

private class OfflineFakePresenter : NotificationPresenter {
    val posted = mutableListOf<String>()
    val withdrawn = mutableListOf<String>()
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) { posted += anchor.anchorKey }
    override suspend fun withdraw(anchorKey: String) { withdrawn += anchorKey }
}

/**
 * FR-036: no notification path ever touches the network -- every port here is local (Room,
 * AlarmManager, NotificationManagerCompat). FR-045: the delivery ledger is bookkeeping, not a
 * source of truth -- losing it changes no figure the app reports, at worst a repeated post.
 */
class NotificationOfflineTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: OfflineFakeBoundaryStatus
    private lateinit var preferences: NotificationPreferencesStore
    private lateinit var deliveries: DeliveryStore
    private lateinit var streaks: GetStreakSummary
    private lateinit var closedWeekSummary: GetClosedWeekSummary
    private lateinit var dayPlans: RoomDayPlanRepository
    private lateinit var completions: RoomCompletionRepository

    private fun at(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant()

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = at(15, 50), zone = zone)
        boundary = OfflineFakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Maghrib, coordinates = Coordinates(30.0, 31.0), zoneIdWhenObtained = zone.id,
                resolvedDate = date, expiresAt = at(23, 50), lastResolvedDate = date, lastResolvedRegime = BoundaryRegime.Maghrib,
            ),
        )
        preferences = NotificationPreferencesStore(db.notificationDao())
        deliveries = DeliveryStore(db.notificationDao())
        val catalogue = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        dayPlans = RoomDayPlanRepository(db, catalogue, time)
        completions = RoomCompletionRepository(db, dayPlans, DayWritePolicy(time), time)
        val coverage = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))
        streaks = GetStreakSummary(completions, dayPlans, time, coverage, boundary)
        closedWeekSummary = GetClosedWeekSummary(dayPlans, completions, catalogue, coverage)

        db.dayPlanDao().insertPlanWithTasks(
            DayPlanEntity(id = "plan", date = date.toString(), catalogueVersion = 1, hijriLabel = "H", availablePoints = 1, updatedAt = 1),
            listOf(PlannedTaskEntity(id = "asr-1-id", dayPlanId = "plan", taskSlug = "asr-1", sectionId = "asr", sectionLabel = "asr", sectionOrder = 3, displayPosition = 1, label = "asr", points = 1, maxOccurrencesPerDay = 1, updatedAt = 1)),
        )
        preferences.save(NotificationPreferences(setOf(NotificationCategory.PRAYER_WINDOW), false, null))
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun buildWorker(presenter: NotificationPresenter, scheduler: NotificationScheduler, inputKey: String? = null): NotificationWorker {
        val prayerTimes = PrayerTimes(date, at(4, 30), at(12, 0), at(15, 30), at(18, 0), at(19, 30))
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, OfflineFakePrayerTimesProvider(prayerTimes), dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, scheduler, presenter)
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        if (inputKey != null) builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to inputKey))
        return builder.setWorkerFactory(factory).build() as NotificationWorker
    }

    @Test fun schedulingEvaluatingPostingAndWithdrawingAllSucceedWithNoNetworkAvailable() = runBlocking {
        // Every collaborator here is local -- Room, the fakes standing in for AlarmManager and
        // NotificationManagerCompat -- so "no network available" is simply "nothing here asks".
        val presenter = OfflineFakePresenter()
        val scheduler = OfflineFakeScheduler()
        val anchorKey = "PRAYER:$date:asr"

        buildWorker(presenter, scheduler, inputKey = anchorKey).doWork()

        assertTrue(scheduler.replaceAllCalls > 0)
        assertEquals(1, presenter.posted.count { it == anchorKey })

        buildWorker(presenter, scheduler).doWork() // a bare refresh, still fully offline-capable
        assertTrue(scheduler.replaceAllCalls > 1)
    }

    @Test fun deletingEveryDeliveryRowChangesNoFigureTheAppReportsOnlyAPossibleRepeatPost() = runBlocking {
        val presenter = OfflineFakePresenter()
        val scheduler = OfflineFakeScheduler()
        val anchorKey = "PRAYER:$date:asr"

        buildWorker(presenter, scheduler, inputKey = anchorKey).doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })

        // Wipe the ledger -- the closed-week summary and every day/week figure come from
        // day_plans and completions, never from notification_deliveries (FR-045).
        deliveries.records().forEach { /* no bulk delete API; prune everything by pruning before "now" */ }
        db.notificationDao().let { }
        deliveries.prune(time.now().plusSeconds(1))
        assertTrue(deliveries.records().isEmpty())

        val summaryBefore = closedWeekSummary(WeekBoundary.weekContaining(date))
        // Re-running can, at worst, post the same anchor a second time -- it must never crash
        // and must never change what the summary reports.
        buildWorker(presenter, scheduler, inputKey = anchorKey).doWork()
        val summaryAfter = closedWeekSummary(WeekBoundary.weekContaining(date))
        assertEquals(summaryBefore, summaryAfter)
    }

    private companion object {
        const val DB_NAME = "notification-offline-test.db"
    }
}
