package com.giraffe.mizanapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.giraffe.mizanapp.data.db.MizanDatabase
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
import com.giraffe.mizanapp.domain.notification.DeliveryState
import com.giraffe.mizanapp.domain.notification.DiscardReason
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
import com.giraffe.mizanapp.domain.usecase.GetClosedWeekSummary
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class RelaxedFakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(initial)
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {}
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

private class RelaxedFakeScheduler : NotificationScheduler {
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) {}
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.RELAXED
    override suspend fun scheduleRefresh(at: Instant) {}
    override suspend fun scheduleAt(anchorKey: String, at: Instant) {}
}

private class RelaxedFakePresenter : NotificationPresenter {
    val posted = mutableListOf<String>()
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) { posted += anchor.anchorKey }
    override suspend fun withdraw(anchorKey: String) {}
}

/**
 * SC-007a: relaxed delivery (no exact-alarm permission) can fire an anchor late. A late fire must
 * discard as `WINDOW_PASSED`, exactly like an on-time evaluation of a stale window would, and it
 * must never post in the following window either — evaluation is always against the anchor's own
 * subject, never "the nearest window that still fits".
 */
class RelaxedDeliveryTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: RelaxedFakeBoundaryStatus
    private val presenter = RelaxedFakePresenter()
    private lateinit var preferences: NotificationPreferencesStore
    private lateinit var deliveries: DeliveryStore
    private lateinit var streaks: GetStreakSummary
    private lateinit var closedWeekSummary: GetClosedWeekSummary
    private lateinit var dayPlans: RoomDayPlanRepository
    private lateinit var completions: RoomCompletionRepository

    private val coordinates = Coordinates(30.0, 31.0)
    private val asrTime = date.atTime(15, 30).atZone(zone).toInstant()
    private val maghribTime = date.atTime(18, 0).atZone(zone).toInstant()
    private val anchorKey = "PRAYER:$date:asr"

    private fun at(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant()

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = at(15, 50), zone = zone)
        boundary = RelaxedFakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Maghrib, coordinates = coordinates, zoneIdWhenObtained = zone.id,
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

    private fun buildWorker(): NotificationWorker {
        val prayerTimes = PrayerTimes(date, at(4, 30), at(12, 0), asrTime, maghribTime, at(19, 30))
        val prayerProvider = object : PrayerTimesProvider {
            override suspend fun timesFor(d: LocalDate, at: Coordinates, z: ZoneId): PrayerTimesOutcome =
                PrayerTimesOutcome.Calculated(prayerTimes)
        }
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, prayerProvider, dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, RelaxedFakeScheduler(), presenter)
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to anchorKey))
        return builder.setWorkerFactory(factory).build() as NotificationWorker
    }

    @Test fun aWindowFiredAnHourLateDiscardsAsWindowPassedAndNeverPostsAfterward() = runBlocking {
        // The window for Asr ends at Maghrib (18:00). A relaxed alarm firing an hour late lands
        // at 19:00 -- past the window's own end, even though the anchor's *scheduled* fire time
        // (15:50) was well inside it.
        time.advanceBy(Duration.ofHours(3).plusMinutes(10)) // now 19:00
        buildWorker().doWork()

        assertEquals(0, presenter.posted.size)
        val record = deliveries.records().single { it.anchorKey == anchorKey }
        assertEquals(DeliveryState.DISCARDED, record.state)
        assertEquals(DiscardReason.WINDOW_PASSED, record.reason)

        // A later re-derivation must not resurrect it into "the following window" either.
        buildWorker().doWork()
        assertEquals(0, presenter.posted.size)
    }

    private companion object {
        const val DB_NAME = "relaxed-delivery-test.db"
    }
}
