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
import com.giraffe.mizanapp.domain.notification.QuietHours
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

private class HistoryFakeBoundaryStatus(initial: BoundaryState) : BoundaryStatus {
    private val stateFlow = MutableStateFlow(initial)
    fun setState(s: BoundaryState) { stateFlow.value = s }
    override fun current(): BoundaryState = stateFlow.value
    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()
    override suspend fun refresh(now: Instant, zone: ZoneId) {}
    override suspend fun requestLocation(): LocationRequestOutcome = LocationRequestOutcome.Obtained
    override suspend fun eraseLocation() {}
    override fun promptShown(): Boolean = true
    override suspend fun markPromptShown() {}
}

private class HistoryFakeScheduler : NotificationScheduler {
    override suspend fun replaceAll(anchors: List<NotificationAnchor>) {}
    override suspend fun cancelAll() {}
    override fun deliveryMode(): DeliveryMode = DeliveryMode.EXACT
    override suspend fun scheduleRefresh(at: Instant) {}
    override suspend fun scheduleAt(anchorKey: String, at: Instant) {}
}

private class HistoryFakePresenter : NotificationPresenter {
    override fun hasPermission(): Boolean = true
    override suspend fun post(anchor: NotificationAnchor, content: NotificationContent) {}
    override suspend fun withdraw(anchorKey: String) {}
}

/**
 * SC-012, FR-044: this feature writes no history. A week of full notification activity — several
 * posts, a held-then-posted summary, a simulated reboot, and a ledger prune — must leave every
 * column of `day_plans` and `completions` exactly as seeded.
 */
class HistoryUntouchedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var boundary: HistoryFakeBoundaryStatus
    private lateinit var preferences: NotificationPreferencesStore
    private lateinit var deliveries: DeliveryStore
    private lateinit var streaks: GetStreakSummary
    private lateinit var closedWeekSummary: GetClosedWeekSummary
    private lateinit var dayPlans: RoomDayPlanRepository
    private lateinit var completions: RoomCompletionRepository

    private val week = WeekBoundary.weekContaining(LocalDate.of(2026, 9, 1))
    private val coordinates = Coordinates(30.0, 31.0)

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider(instant = week.start.atTime(9, 0).atZone(zone).toInstant(), zone = zone)
        boundary = HistoryFakeBoundaryStatus(
            BoundaryState(
                regime = BoundaryRegime.Maghrib, coordinates = coordinates, zoneIdWhenObtained = zone.id,
                resolvedDate = week.start, expiresAt = week.start.atTime(23, 50).atZone(zone).toInstant(),
                lastResolvedDate = week.start, lastResolvedRegime = BoundaryRegime.Maghrib,
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

        // Seed a full week of day plans, each with one recorded task.
        week.dates.forEachIndexed { i, date ->
            db.dayPlanDao().insertPlanWithTasks(
                DayPlanEntity(id = "plan-$i", date = date.toString(), catalogueVersion = 1, hijriLabel = "H", availablePoints = 1, updatedAt = 1),
                listOf(PlannedTaskEntity(id = "t-$i-id", dayPlanId = "plan-$i", taskSlug = "asr-1", sectionId = "asr", sectionLabel = "asr", sectionOrder = 3, displayPosition = 1, label = "asr", points = 1, maxOccurrencesPerDay = 1, updatedAt = 1)),
            )
            db.completionDao().insert(CompletionEntity(id = "c-$i", dayPlanId = "plan-$i", taskSlug = "asr-1", creditedDate = date.toString(), pointsAwarded = 1, recordedAt = 1, updatedAt = 1))
        }
        preferences.save(NotificationPreferences(setOf(NotificationCategory.PRAYER_WINDOW, NotificationCategory.STREAK_AT_RISK, NotificationCategory.WEEKLY_SUMMARY), false, QuietHours(LocalTime.of(20, 0), LocalTime.of(20, 30))))
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
    }

    private fun buildWorker(inputKey: String? = null): NotificationWorker {
        val prayerTimes = PrayerTimes(time.today(), at(4, 30), at(12, 0), at(15, 30), at(18, 0), at(19, 30))
        val prayerProvider = object : PrayerTimesProvider {
            override suspend fun timesFor(d: LocalDate, at: Coordinates, z: ZoneId): PrayerTimesOutcome = PrayerTimesOutcome.Calculated(prayerTimes)
        }
        val factory = object : androidx.work.WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                NotificationWorker(appContext, workerParameters, time, boundary, prayerProvider, dayPlans, completions, streaks, closedWeekSummary, preferences, deliveries, HistoryFakeScheduler(), HistoryFakePresenter())
        }
        val builder = TestListenableWorkerBuilder<NotificationWorker>(context)
        if (inputKey != null) builder.setInputData(androidx.work.workDataOf(NotificationWorker.INPUT_ANCHOR_KEY to inputKey))
        return builder.setWorkerFactory(factory).build() as NotificationWorker
    }

    private fun at(hour: Int, minute: Int) = time.today().atTime(hour, minute).atZone(zone).toInstant()

    private fun snapshotTable(table: String): List<Map<String, Any?>> {
        val cursor = db.query("SELECT * FROM $table ORDER BY id", null)
        val rows = mutableListOf<Map<String, Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (col in 0 until it.columnCount) row[it.getColumnName(col)] = it.getString(col)
                rows += row
            }
        }
        return rows
    }

    @Test fun aWeekOfFullNotificationActivityLeavesHistoryByteForByteIdentical() = runBlocking {
        val plansBefore = snapshotTable("day_plans")
        val tasksBefore = snapshotTable("planned_tasks")
        val completionsBefore = snapshotTable("completions")

        // A week of full activity: refresh, a prayer-window post, a streak-at-risk day, quiet
        // hours holding then releasing the summary, a reboot, and a ledger prune.
        buildWorker().doWork()
        time.advanceBy(Duration.ofDays(1))
        boundary.setState(boundary.current().copy(resolvedDate = time.today(), expiresAt = at(23, 50)))
        buildWorker(inputKey = "PRAYER:${time.today()}:asr").doWork()

        time.advanceBy(Duration.ofDays(1))
        boundary.setState(boundary.current().copy(resolvedDate = time.today(), expiresAt = at(23, 50)))
        buildWorker(inputKey = "STREAK:${time.today()}").doWork()

        boundary.setState(boundary.current().copy(resolvedDate = week.end, expiresAt = week.end.atTime(23, 50).atZone(zone).toInstant()))
        time.setDate(week.end)
        buildWorker(inputKey = "WEEK:${week.key.value}").doWork() // held, inside quiet hours default? save below sets a window
        buildWorker(inputKey = "WEEK:${week.key.value}").doWork()

        // Simulate a reboot.
        db.close()
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        deliveries = DeliveryStore(db.notificationDao())
        deliveries.prune(time.now())

        val plansAfter = snapshotTable("day_plans")
        val tasksAfter = snapshotTable("planned_tasks")
        val completionsAfter = snapshotTable("completions")

        assertEquals(plansBefore, plansAfter)
        assertEquals(tasksBefore, tasksAfter)
        assertEquals(completionsBefore, completionsAfter)
    }

    private companion object {
        const val DB_NAME = "history-untouched-test.db"
    }
}
