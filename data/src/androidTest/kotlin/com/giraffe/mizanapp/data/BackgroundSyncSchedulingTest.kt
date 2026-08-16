package com.giraffe.mizanapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.repository.NoOpCataloguePublicationRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.data.sync.SyncScheduler
import com.giraffe.mizanapp.data.sync.SyncWorker
import com.giraffe.mizanapp.data.sync.dto.RemoteDayRecord
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Whatever is recorded while offline reaches the account within about a
 * minute of connectivity returning, with the app closed and no user action
 * (FR-016, SC-003). WorkManager's own constraint release is what makes this
 * real rather than a fixed poll. The worker is built through a small
 * hand-written [WorkerFactory] here rather than Koin's, since `:data` — by
 * design — has no DI-framework dependency at all.
 */
class BackgroundSyncSchedulingTest {

    private lateinit var context: Context
    private lateinit var db: MizanDatabase
    private lateinit var time: TestTimeProvider
    private lateinit var fake: FakeRemoteDataSource
    private lateinit var engine: SyncEngine
    private lateinit var outbox: Outbox
    private lateinit var accountScope: AccountScope

    private val userId = "user-1"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB_NAME)
        db = Room.databaseBuilder(context, MizanDatabase::class.java, DB_NAME).build()
        time = TestTimeProvider()
        fake = FakeRemoteDataSource().apply { currentUserId = userId; unreachable = true }
        outbox = Outbox(db, time)
        accountScope = AccountScope(db.accountScopeDao(), time)
        engine = SyncEngine(db, outbox, accountScope, fake, time)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == SyncWorker::class.java.name) {
                SyncWorker(appContext, workerParameters, engine, NoOpCataloguePublicationRepository())
            } else {
                null
            }
        }

        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun recording_while_unreachable_schedules_and_drains_once_connectivity_returns() = runBlocking {
        accountScope.set(userId, "user@example.test", null)
        db.dayPlanDao().insertPlan(
            com.giraffe.mizanapp.data.db.entities.DayPlanEntity(
                id = "plan-1",
                date = "2026-08-16",
                catalogueVersion = 1,
                hijriLabel = "X",
                availablePoints = 69,
                updatedAt = time.now().toEpochMilli(),
            ),
        )
        engine.claimLocalRecords(userId)
        outbox.enqueue(
            OutboxEntry(
                entityType = OutboxEntry.EntityType.DAY_RECORD,
                entityId = "2026-08-16",
                operation = OutboxEntry.Operation.UPSERT,
                payload = Json.encodeToString(RemoteDayRecord(userId = userId, date = "2026-08-16", catalogueVersion = 1)),
            ),
        )
        SyncScheduler(context).schedule()

        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork(SyncScheduler.WORK_NAME).get()
        assertEquals(1, workInfos.size)
        val info = workInfos.single()
        assertTrue("expected a CONNECTED network constraint", info.constraints.requiredNetworkType == NetworkType.CONNECTED)

        fake.unreachable = false
        val driver = requireNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        driver.setAllConstraintsMet(info.id)

        val finished = awaitTerminalState(workManager, info.id, timeoutMs = 10_000)
        assertEquals(WorkInfo.State.SUCCEEDED, finished)

        assertEquals(0, db.outboxDao().count())
        val plan = requireNotNull(db.dayPlanDao().planByDate("2026-08-16"))
        assertTrue("syncedAt must be set once the worker drains", plan.plan.syncedAt != null)
    }

    private suspend fun awaitTerminalState(workManager: WorkManager, id: UUID, timeoutMs: Long): WorkInfo.State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = workManager.getWorkInfoById(id).get()?.state
            if (state != null && state.isFinished) return state
            delay(50)
        }
        error("work did not reach a terminal state within ${timeoutMs}ms")
    }

    private companion object {
        const val DB_NAME = "mizan-worker-test.db"
    }
}
