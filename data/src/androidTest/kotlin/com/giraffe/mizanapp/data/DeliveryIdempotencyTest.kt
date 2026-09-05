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
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.FallbackReason
import com.giraffe.mizanapp.domain.time.LocationRequestOutcome
import com.giraffe.mizanapp.domain.time.WeekBoundary
import com.giraffe.mizanapp.domain.usecase.GetClosedWeekSummary
import com.giraffe.mizanapp.domain.usecase.GetStreakSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class IdemFakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(initial)
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {}
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

private class IdemFakePrayerTimesProvider : PrayerTimesProvider {
    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome = PrayerTimesOutcome.NoLocation
}

private class IdemFakeScheduler : NotificationScheduler {
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) {}
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
    override suspend fun scheduleRefresh(at: Instant) {}
    override suspend fun scheduleAt(anchorKey: String, at: Instant) {}
}

private class IdemFakePresenter : NotificationPresenter {
    val posted = mutableListOf<String>()
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) { posted += anchor.anchorKey }
    override suspend fun withdraw(anchorKey: String) {}
}

/**
 * SC-013: once an anchor is delivered, the ledger's `anchorKey` primary key is the idempotency
 * guarantee — never application logic layered on top (the fifth way this feature can go wrong,
 * tasks.md). A reboot, a clock moving backwards, a time-zone change, and repeated re-derivation
 * must never produce a second post for the same anchor.
 */
class DeliveryIdempotencyTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: IdemFakeBoundaryStatus
    private val presenter = IdemFakePresenter()
    private lateinit var preferences: NotificationPreferencesStore
    private lateinit var deliveries: DeliveryStore
    private lateinit var streaks: GetStreakSummary
    private lateinit var closedWeekSummary: GetClosedWeekSummary
    private lateinit var dayPlans: RoomDayPlanRepository
    private lateinit var completions: RoomCompletionRepository

    private val week = WeekBoundary.weekContaining(LocalDate.of(2026, 9, 1))
    private val friday get() = week.end
    private val anchorKey get() = "WEEK:${week.key.value}"

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        openDb()
        db.dayPlanDao().insertPlanWithTasks(
            DayPlanEntity(id = "plan", date = week.start.toString(), catalogueVersion = 1, hijriLabel = "H", availablePoints = 1, updatedAt = 1),
            listOf(PlannedTaskEntity(id = "t-id", dayPlanId = "plan", taskSlug = "t", sectionId = "fajr", sectionLabel = "fajr", sectionOrder = 1, displayPosition = 1, label = "fajr", points = 1, maxOccurrencesPerDay = 1, updatedAt = 1)),
        )
        db.completionDao().insert(CompletionEntity(id = "c", dayPlanId = "plan", taskSlug = "t", creditedDate = week.start.toString(), pointsAwarded = 1, recordedAt = 1, updatedAt = 1))
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, null))
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun openDb() {
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = friday.atTime(20, 0).atZone(zone).toInstant(), zone = zone)
        boundary = IdemFakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION), coordinates = null,
                zoneIdWhenObtained = null, resolvedDate = friday, expiresAt = friday.atTime(23, 0).atZone(zone).toInstant(),
                lastResolvedDate = friday, lastResolvedRegime = null,
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
    }

    /** Simulates a reboot: closes and reopens the same on-disk database, keeping the clock. */
    private fun reboot() {
        val savedTime = time
        db.close()
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = savedTime
        boundary = IdemFakeBoundaryStatus(boundary.current())
        preferences = NotificationPreferencesStore(db.notificationDao())
        deliveries = DeliveryStore(db.notificationDao())
        val catalogue = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        dayPlans = RoomDayPlanRepository(db, catalogue, time)
        completions = RoomCompletionRepository(db, dayPlans, DayWritePolicy(time), time)
        val coverage = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))
        streaks = GetStreakSummary(completions, dayPlans, time, coverage, boundary)
        closedWeekSummary = GetClosedWeekSummary(dayPlans, completions, catalogue, coverage)
    }

    private fun buildWorker(): NotificationWorker {
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, IdemFakePrayerTimesProvider(), dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, IdemFakeScheduler(), presenter)
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to anchorKey))
        return builder.setWorkerFactory(factory).build() as NotificationWorker
    }

    @Test fun exactlyOnePostAcrossRebootClockRewindZoneChangeAndRepeatedRederivation() = runBlocking {
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })

        reboot()
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })

        time.advanceBy(Duration.ofHours(-2))
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })

        time.setZone(ZoneId.of("Asia/Jakarta"))
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })

        repeat(3) { buildWorker().doWork() }
        assertEquals(1, presenter.posted.count { it == anchorKey })
    }

    private companion object {
        const val DB_NAME = "delivery-idempotency-test.db"
    }
}
