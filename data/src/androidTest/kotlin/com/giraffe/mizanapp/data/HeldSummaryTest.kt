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
import com.giraffe.mizanapp.domain.notification.AnchorSubject
import com.giraffe.mizanapp.domain.notification.DeliveryMode
import com.giraffe.mizanapp.domain.notification.DeliveryState
import com.giraffe.mizanapp.domain.notification.NotificationAnchor
import com.giraffe.mizanapp.domain.notification.NotificationCategory
import com.giraffe.mizanapp.domain.notification.NotificationContent
import com.giraffe.mizanapp.domain.notification.NotificationPresenter
import com.giraffe.mizanapp.domain.notification.NotificationPreferences
import com.giraffe.mizanapp.domain.notification.NotificationScheduler
import com.giraffe.mizanapp.domain.notification.QuietHours
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

private class HeldFakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(initial)
    fun setState(state: BoundaryState) { stateFlow.value = state }
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {}
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

private class HeldFakePrayerTimesProvider : PrayerTimesProvider {
    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome = PrayerTimesOutcome.NoLocation
}

private class HeldFakeScheduler : NotificationScheduler {
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) {}
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
    override suspend fun scheduleRefresh(at: Instant) {}
    override suspend fun scheduleAt(anchorKey: String, at: Instant) {}
}

private class HeldFakePresenter : NotificationPresenter {
    val posted = mutableListOf<String>()
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) { posted += anchor.anchorKey }
    override suspend fun withdraw(anchorKey: String) {}
}

/**
 * T051: the quiet-hours Hold path for the weekly summary. A held summary must post exactly once,
 * whenever quiet hours finally end — even if quiet hours themselves change while it waits (FR-035).
 */
class HeldSummaryTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: HeldFakeBoundaryStatus
    private lateinit var presenter: HeldFakePresenter
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
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = friday.atTime(20, 30).atZone(zone).toInstant(), zone = zone)
        boundary = HeldFakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION),
                coordinates = null,
                zoneIdWhenObtained = null,
                resolvedDate = friday,
                expiresAt = friday.atTime(23, 0).atZone(zone).toInstant(),
                lastResolvedDate = friday,
                lastResolvedRegime = null,
            ),
        )
        presenter = HeldFakePresenter()
        preferences = NotificationPreferencesStore(db.notificationDao())
        deliveries = DeliveryStore(db.notificationDao())
        val catalogue = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        dayPlans = RoomDayPlanRepository(db, catalogue, time)
        completions = RoomCompletionRepository(db, dayPlans, DayWritePolicy(time), time)
        val coverage = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))
        streaks = GetStreakSummary(completions, dayPlans, time, coverage, boundary)
        closedWeekSummary = GetClosedWeekSummary(dayPlans, completions, catalogue, coverage)

        // Seed one recorded task inside this week so the summary reads as active, not dormant.
        db.dayPlanDao().insertPlanWithTasks(
            DayPlanEntity(id = "plan", date = week.start.toString(), catalogueVersion = 1, hijriLabel = "H", availablePoints = 1, updatedAt = 1),
            listOf(PlannedTaskEntity(id = "t-id", dayPlanId = "plan", taskSlug = "t", sectionId = "fajr", sectionLabel = "fajr", sectionOrder = 1, displayPosition = 1, label = "fajr", points = 1, maxOccurrencesPerDay = 1, updatedAt = 1)),
        )
        db.completionDao().insert(CompletionEntity(id = "c", dayPlanId = "plan", taskSlug = "t", creditedDate = week.start.toString(), pointsAwarded = 1, recordedAt = 1, updatedAt = 1))
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun buildWorker(): NotificationWorker {
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, HeldFakePrayerTimesProvider(), dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, HeldFakeScheduler(), presenter)
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to anchorKey))
        return builder.setWorkerFactory(factory).build() as NotificationWorker
    }

    private fun setNow(hour: Int, minute: Int, date: LocalDate = friday) {
        time.setDate(date)
        time.advanceBy(java.time.Duration.between(time.now(), date.atTime(hour, minute).atZone(zone).toInstant()))
    }

    @Test fun heldSummaryPostsExactlyOnceThenDiscardsAsAlreadyDelivered() = runBlocking {
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(20, 0), LocalTime.of(21, 0))))
        setNow(20, 30)
        buildWorker().doWork()
        val held = deliveries.records().single { it.anchorKey == anchorKey }
        assertEquals(DeliveryState.HELD, held.state)
        assertEquals(friday.atTime(21, 0).atZone(zone).toInstant(), held.heldUntil)

        setNow(21, 0)
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })
        assertEquals(DeliveryState.DELIVERED, deliveries.records().single { it.anchorKey == anchorKey }.state)

        setNow(21, 5)
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })
    }

    @Test fun quietWindowCoveringAlmostTheWholeDayStillPostsOnceAtItsEnd() = runBlocking {
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(0, 0), LocalTime.of(23, 59))))
        setNow(10, 0)
        buildWorker().doWork()
        assertEquals(DeliveryState.HELD, deliveries.records().single { it.anchorKey == anchorKey }.state)

        setNow(23, 59)
        buildWorker().doWork()
        assertEquals(1, presenter.posted.count { it == anchorKey })
    }

    @Test fun editingQuietHoursWhileHeldDoesNotPostTwice() = runBlocking {
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(20, 0), LocalTime.of(21, 0))))
        setNow(20, 30)
        buildWorker().doWork()

        // Widen the window before the original heldUntil is reached.
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(20, 0), LocalTime.of(22, 0))))
        setNow(21, 0)
        buildWorker().doWork()
        assertEquals(0, presenter.posted.size)
        assertEquals(friday.atTime(22, 0).atZone(zone).toInstant(), deliveries.records().single { it.anchorKey == anchorKey }.heldUntil)

        // Narrow it again; by the time the new heldUntil arrives the window has already ended.
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(20, 0), LocalTime.of(21, 30))))
        setNow(22, 0)
        buildWorker().doWork()

        assertEquals(1, presenter.posted.count { it == anchorKey })
    }

    private companion object {
        const val DB_NAME = "held-summary-test.db"
    }
}
