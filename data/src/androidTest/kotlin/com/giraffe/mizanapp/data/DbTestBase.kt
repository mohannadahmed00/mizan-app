package com.giraffe.mizanapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.repository.RoomCatalogueRepository
import com.giraffe.mizanapp.data.repository.RoomCompletionRepository
import com.giraffe.mizanapp.data.repository.RoomDayPlanRepository
import com.giraffe.mizanapp.data.repository.RoomRecordCoverageRepository
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.time.DayBoundary
import com.giraffe.mizanapp.domain.time.TimeProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Before

/** A clock the instrumented tests control. No test may read the real one. */
class TestTimeProvider(
    private var instant: Instant = Instant.parse("2026-03-14T09:00:00Z"),
    private var zone: ZoneId = ZoneId.of("Africa/Cairo"),
) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(): LocalDate = DayBoundary.dateAt(instant, zone, null)
    override fun zone(): ZoneId = zone
    fun advanceBy(duration: Duration) { instant = instant.plus(duration) }
    fun setDate(date: LocalDate) { instant = date.atTime(9, 0).atZone(zone).toInstant() }
    fun setZone(newZone: ZoneId) { zone = newZone }
}

abstract class DbTestBase {

    protected val context: android.content.Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    protected lateinit var db: MizanDatabase
    protected lateinit var time: TestTimeProvider
    protected lateinit var catalogue: RoomCatalogueRepository
    protected lateinit var dayPlans: RoomDayPlanRepository
    protected lateinit var completions: RoomCompletionRepository
    protected lateinit var coverageRepo: RoomRecordCoverageRepository

    @Before
    fun setUpDatabase() {
        context.deleteDatabase(TEST_DB)
        openDatabase()
    }

    @After
    fun closeDatabase() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    /**
     * Opens (or reopens) the database and rebuilds the repositories on it.
     *
     * File-backed rather than in-memory, deliberately: an in-memory database is
     * destroyed on close, so it could not demonstrate that records survive a
     * reopen — which is the cheap proxy for process death (SC-005).
     */
    protected fun openDatabase(keepTime: Boolean = false) {
        if (!keepTime) time = TestTimeProvider()
        db = Room.databaseBuilder(context, MizanDatabase::class.java, TEST_DB).build()
        wireRepositories()
    }

    private fun wireRepositories() {
        catalogue = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        dayPlans = RoomDayPlanRepository(db, catalogue, time)
        completions = RoomCompletionRepository(db, dayPlans, DayWritePolicy(time), time)
        coverageRepo = RoomRecordCoverageRepository(db, AccountScope(db.accountScopeDao(), time))
    }

    protected suspend fun seedAndPlanToday() {
        catalogue.seedIfNeeded()
        dayPlans.ensurePlanFor(time.today())
    }

    private companion object {
        const val TEST_DB = "mizan-test.db"
    }
}
