package com.giraffe.mizanapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
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
import com.giraffe.mizanapp.domain.notification.DeliveryRecord
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
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.LocationRequestOutcome
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

private class FakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    val refreshCalls = mutableListOf<Instant>()
    var refreshedBeforeAnyRead = false
    private val stateFlow = MutableStateFlow(initial)
    fun setState(state: BoundaryState) { stateFlow.value = state }
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {
        refreshCalls += now
        refreshedBeforeAnyRead = true
    }
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

private class FakePrayerTimesProvider : PrayerTimesProvider {
    override suspend fun timesFor(date: LocalDate, at: Coordinates, zone: ZoneId): PrayerTimesOutcome = PrayerTimesOutcome.NoLocation
}

private class FakeNotificationScheduler : NotificationScheduler {
    val replaceAllCalls = mutableListOf<List<NotificationAnchor>>()
    var refreshScheduledAt: Instant? = null
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) { replaceAllCalls += anchors }
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
    override suspend fun scheduleRefresh(at: Instant) { refreshScheduledAt = at }
}

private class FakeNotificationPresenter : NotificationPresenter {
    val posted = mutableListOf<String>()
    val withdrawn = mutableListOf<String>()
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) { posted += anchor.anchorKey }
    override suspend fun withdraw(anchorKey: String) { withdrawn += anchorKey }
}

class NotificationWorkerTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: FakeBoundaryStatus
    private lateinit var scheduler: FakeNotificationScheduler
    private lateinit var presenter: FakeNotificationPresenter
    private lateinit var preferences: NotificationPreferencesStore
    private lateinit var deliveries: DeliveryStore
    private lateinit var streaks: GetStreakSummary
    private lateinit var closedWeekSummary: GetClosedWeekSummary
    private lateinit var dayPlans: RoomDayPlanRepository
    private lateinit var completions: RoomCompletionRepository

    private val date = LocalDate.of(2026, 9, 4)
    private val dayEndsAt = Instant.parse("2026-09-04T20:50:00Z")

    @Before fun setUp() {
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = Instant.parse("2026-09-04T12:30:00Z"), zone = ZoneId.of("Africa/Cairo"))
        boundary = FakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Fallback(com.giraffe.mizanapp.domain.time.FallbackReason.NEVER_HAD_LOCATION),
                coordinates = null,
                zoneIdWhenObtained = null,
                resolvedDate = date,
                expiresAt = dayEndsAt,
                lastResolvedDate = date,
                lastResolvedRegime = null,
            ),
        )
        scheduler = FakeNotificationScheduler()
        presenter = FakeNotificationPresenter()
        preferences = NotificationPreferencesStore(db.notificationDao())
        deliveries = DeliveryStore(db.notificationDao())
        val catalogue = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        dayPlans = RoomDayPlanRepository(db, catalogue, time)
        completions = RoomCompletionRepository(db, dayPlans, DayWritePolicy(time), time)
        val coverage = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))
        streaks = GetStreakSummary(completions, dayPlans, time, coverage, boundary)
        closedWeekSummary = GetClosedWeekSummary(dayPlans, completions, catalogue, coverage)
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    private suspend fun seedPlan(sectionId: String = "asr", slug: String = "$sectionId-1") {
        db.dayPlanDao().insertPlanWithTasks(
            DayPlanEntity(id = "plan", date = date.toString(), catalogueVersion = 1, hijriLabel = "H", availablePoints = 1, updatedAt = 1),
            listOf(
                PlannedTaskEntity(
                    id = "$slug-id", dayPlanId = "plan", taskSlug = slug, sectionId = sectionId, sectionLabel = sectionId,
                    sectionOrder = 1, displayPosition = 1, label = sectionId, points = 1, maxOccurrencesPerDay = 1, updatedAt = 1,
                ),
            ),
        )
    }

    private fun buildWorker(anchorKeyInput: String? = null): ListenableWorker {
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, FakePrayerTimesProvider(), dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, scheduler, presenter)
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        anchorKeyInput?.let { builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to it)) }
        return builder.setWorkerFactory(factory).build()
    }

    @Test fun refreshesBoundaryStatusBeforeReadingAnything() = runBlocking {
        seedPlan()
        val worker = buildWorker()
        (worker as NotificationWorker).doWork()
        assertTrue(boundary.refreshedBeforeAnyRead)
        assertEquals(1, boundary.refreshCalls.size)
    }

    @Test fun actsOnEvaluateAnchorVerdictRatherThanDecidingItself() = runBlocking {
        // Section already complete -> evaluateAnchor must yield Discard(SECTION_COMPLETE) for a
        // triggering prayer anchor, and the worker must not post.
        seedPlan()
        db.completionDao().insert(CompletionEntity(id = "c1", dayPlanId = "plan", taskSlug = "asr-1", creditedDate = date.toString(), pointsAwarded = 1, recordedAt = time.now().toEpochMilli(), updatedAt = time.now().toEpochMilli()))
        preferences.save(NotificationPreferences(setOf(NotificationCategory.PRAYER_WINDOW), false, null))
        val key = NotificationAnchor(NotificationCategory.PRAYER_WINDOW, time.now().minusSeconds(60), AnchorSubject.PrayerWindow(date, "asr", time.now().plusSeconds(600))).anchorKey
        val worker = buildWorker(key) as NotificationWorker
        worker.doWork()
        assertTrue(key !in presenter.posted)
    }

    @Test fun writesExactlyOneLedgerRowPerRunWhenATriggeringAnchorMatches() = runBlocking {
        seedPlan()
        preferences.save(NotificationPreferences(setOf(NotificationCategory.STREAK_AT_RISK), false, null))
        val anchor = NotificationAnchor(NotificationCategory.STREAK_AT_RISK, time.now().minusSeconds(10), AnchorSubject.Day(date))
        val worker = buildWorker(anchor.anchorKey) as NotificationWorker
        worker.doWork()
        assertEquals(1, deliveries.records().size)
    }

    @Test fun handsPlanAnchorsToReplaceAllOnEveryRunIncludingBareRefresh() = runBlocking {
        seedPlan()
        val worker = buildWorker() as NotificationWorker
        worker.doWork()
        assertEquals(1, scheduler.replaceAllCalls.size)
    }

    @Test fun schedulesRefreshAtEvenWhenAnchorsIsEmpty() = runBlocking {
        preferences.save(NotificationPreferences(emptySet(), false, null))
        val worker = buildWorker() as NotificationWorker
        worker.doWork()
        assertTrue(scheduler.replaceAllCalls.single().isEmpty())
        assertEquals(dayEndsAt, scheduler.refreshScheduledAt)
    }

    @Test fun dormantSummaryProducesNoWeeklySummaryAnchor() = runBlocking {
        // No day plans anywhere -> every closed week reads as empty -> dormant after three.
        preferences.save(NotificationPreferences(setOf(NotificationCategory.WEEKLY_SUMMARY), false, null))
        val friday = com.giraffe.mizanapp.domain.time.WeekBoundary.weekContaining(date).end
        boundary.setState(
            BoundaryState(
                regime = BoundaryRegime.Fallback(com.giraffe.mizanapp.domain.time.FallbackReason.NEVER_HAD_LOCATION),
                coordinates = null,
                zoneIdWhenObtained = null,
                resolvedDate = friday,
                expiresAt = dayEndsAt,
                lastResolvedDate = friday,
                lastResolvedRegime = null,
            ),
        )
        val worker = buildWorker() as NotificationWorker
        worker.doWork()
        assertTrue(scheduler.replaceAllCalls.single().none { it.category == NotificationCategory.WEEKLY_SUMMARY })
    }

    @Test fun prunesLedgerRowsOlderThanNinetyDays() = runBlocking {
        deliveries.record(DeliveryRecord("WEEK:2026-01-01", NotificationCategory.WEEKLY_SUMMARY, DeliveryState.DELIVERED, null, Instant.parse("2026-01-01T00:00:00Z"), null))
        val worker = buildWorker() as NotificationWorker
        worker.doWork()
        assertTrue(deliveries.records().none { it.anchorKey == "WEEK:2026-01-01" })
    }

    private companion object {
        const val DB_NAME = "notification-worker-test.db"
    }
}
