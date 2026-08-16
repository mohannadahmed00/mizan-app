package com.giraffe.mizanapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.repository.RoomCatalogueRepository
import com.giraffe.mizanapp.data.repository.RoomCompletionRepository
import com.giraffe.mizanapp.data.repository.RoomDayPlanRepository
import com.giraffe.mizanapp.data.repository.SyncingCompletionRepository
import com.giraffe.mizanapp.data.repository.SyncingDayPlanRepository
import com.giraffe.mizanapp.data.seed.CatalogueSeeder
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.SyncEngine
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.domain.repository.EnsureOutcome
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.repository.UndoOutcome
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FR-020, SC-004: two devices sharing one account and one [FakeRemoteDataSource]
 * must converge to the same completions, the same earned points and the same
 * available totals — except where FR-024a governs (a day already materialised
 * on one device is never re-derived by what the other device sees).
 *
 * `SyncEngine.pull` (T104) and `SyncEngine.applyRemote` (T103) do not exist yet
 * as anything but a no-op, so nothing pulled by one device ever reaches the
 * other here. This is expected to fail until then.
 */
@RunWith(AndroidJUnit4::class)
class TwoDeviceConvergenceTest {

    private val userId = "user-1"
    private val date: LocalDate = LocalDate.parse("2026-08-16")

    /** One simulated device: its own in-memory database, wired the same way `Modules.kt` wires the real app. */
    private inner class Device {
        val time = TestTimeProvider().apply { setDate(date) }
        val db: MizanDatabase = Room.inMemoryDatabaseBuilder(context(), MizanDatabase::class.java).build()
        private val catalogueRepo = RoomCatalogueRepository(db, CatalogueSeeder(db, time))
        private val dayPlanRoom = RoomDayPlanRepository(db, catalogueRepo, time)
        private val completionRoom = RoomCompletionRepository(db, dayPlanRoom, DayWritePolicy(time), time)
        val outbox = Outbox(db, time)
        val accountScope = AccountScope(db.accountScopeDao(), time)
        lateinit var engine: SyncEngine
        val dayPlans = SyncingDayPlanRepository(dayPlanRoom, outbox, accountScope, db)
        val completions = SyncingCompletionRepository(completionRoom, outbox, accountScope, db)

        suspend fun open(remote: com.giraffe.mizanapp.data.sync.RemoteDataSource) {
            engine = SyncEngine(db, outbox, accountScope, remote, time)
            catalogueRepo.seedIfNeeded()
            engine.migrateOnSignIn(userId)
        }

        /** Push this device's changes, then pull whatever the account now holds. */
        suspend fun sync() {
            engine.drain()
            engine.pull()
        }

        /** Adds a second catalogue version, effective on [date], visible only to this device. */
        suspend fun adoptSecondCatalogueVersion() {
            val dao = db.catalogueDao()
            dao.insertVersions(listOf(CatalogueVersionEntity(version = 2, effectiveFrom = date.toString())))
            val v1 = dao.taskVersionsFor(1)
            dao.insertTaskVersions(
                v1.map { it.copy(id = UUID.randomUUID().toString(), catalogueVersion = 2) },
            )
        }

        fun close() = db.close()
    }

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    // --- concurrent recording on the same day ---

    @Test
    fun concurrent_recording_on_the_same_day_converges() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val a = Device().also { it.open(fake) }
        val b = Device().also { it.open(fake) }

        val planA = requireCreated(a.dayPlans.ensurePlanFor(date))
        requireCreated(b.dayPlans.ensurePlanFor(date))
        val (taskOnA, taskOnB) = planA.plannedTasks.map { it.taskSlug }.let { it[0] to it[1] }

        requireRecorded(a.completions.record(date, taskOnA))
        requireRecorded(b.completions.record(date, taskOnB))

        a.sync(); b.sync(); a.sync(); b.sync()

        val liveA = a.completions.liveBetween(date, date).map { it.taskSlug }.sorted()
        val liveB = b.completions.liveBetween(date, date).map { it.taskSlug }.sorted()
        assertEquals("both devices must see every completion made on either", liveA, liveB)

        val earnedA = a.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        val earnedB = b.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        assertEquals(earnedA, earnedB)

        val availableA = requireNotNull(a.dayPlans.planFor(date)).availablePoints
        val availableB = requireNotNull(b.dayPlans.planFor(date)).availablePoints
        assertEquals(availableA, availableB)

        a.close(); b.close()
    }

    // --- concurrent undo ---

    @Test
    fun concurrent_undo_of_different_completions_converges() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val a = Device().also { it.open(fake) }
        val b = Device().also { it.open(fake) }

        val planA = requireCreated(a.dayPlans.ensurePlanFor(date))
        requireCreated(b.dayPlans.ensurePlanFor(date))
        val (taskOnA, taskOnB) = planA.plannedTasks.map { it.taskSlug }.let { it[0] to it[1] }

        requireRecorded(a.completions.record(date, taskOnA))
        requireRecorded(b.completions.record(date, taskOnB))
        a.sync(); b.sync(); a.sync(); b.sync()

        // Each device must see what the other recorded before it can undo it.
        assertEquals(setOf(taskOnA, taskOnB), a.completions.liveBetween(date, date).map { it.taskSlug }.toSet())
        assertEquals(setOf(taskOnA, taskOnB), b.completions.liveBetween(date, date).map { it.taskSlug }.toSet())

        // Each device undoes the OTHER device's recording, concurrently.
        requireReversed(a.completions.undoLast(date, taskOnB))
        requireReversed(b.completions.undoLast(date, taskOnA))
        a.sync(); b.sync(); a.sync(); b.sync()

        val liveA = a.completions.liveBetween(date, date)
        val liveB = b.completions.liveBetween(date, date)
        assertTrue("nothing recorded by either device should still be live", liveA.isEmpty())
        assertEquals(liveA, liveB)

        a.close(); b.close()
    }

    @Test
    fun one_device_undoes_what_the_other_still_shows() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val a = Device().also { it.open(fake) }
        val b = Device().also { it.open(fake) }

        val planA = requireCreated(a.dayPlans.ensurePlanFor(date))
        val taskSlug = planA.plannedTasks.first().taskSlug

        // Only device A records it, and syncs.
        requireRecorded(a.completions.record(date, taskSlug))
        a.sync()

        // Device B independently opens the same day and pulls the account's state.
        requireCreated(b.dayPlans.ensurePlanFor(date))
        b.sync()

        // B now shows the completion A made. B undoes it.
        val liveOnBBeforeUndo = b.completions.liveBetween(date, date)
        assertTrue("B must have received A's completion before it can undo it", liveOnBBeforeUndo.isNotEmpty())
        requireReversed(b.completions.undoLast(date, taskSlug))
        b.sync(); a.sync()

        // The undo must be visible back on A too.
        assertTrue(a.completions.liveBetween(date, date).isEmpty())
        assertTrue(b.completions.liveBetween(date, date).isEmpty())

        a.close(); b.close()
    }

    // --- independent first-open of the same date ---

    @Test
    fun independent_first_open_under_the_same_catalogue_version_converges() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val a = Device().also { it.open(fake) }
        val b = Device().also { it.open(fake) }

        val planCreatedByA = requireCreated(a.dayPlans.ensurePlanFor(date))
        requireCreated(b.dayPlans.ensurePlanFor(date))
        val (taskOnA, taskOnB) = planCreatedByA.plannedTasks.map { it.taskSlug }.let { it[0] to it[1] }
        requireRecorded(a.completions.record(date, taskOnA))
        requireRecorded(b.completions.record(date, taskOnB))
        a.sync(); b.sync(); a.sync(); b.sync()

        val planA = requireNotNull(a.dayPlans.planFor(date))
        val planB = requireNotNull(b.dayPlans.planFor(date))
        assertEquals(planA.catalogueVersion, planB.catalogueVersion)
        assertEquals(planA.availablePoints, planB.availablePoints)
        assertEquals(
            a.completions.liveBetween(date, date).map { it.taskSlug }.sorted(),
            b.completions.liveBetween(date, date).map { it.taskSlug }.sorted(),
        )
        val earnedA = a.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        val earnedB = b.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        assertEquals(earnedA, earnedB)

        a.close(); b.close()
    }

    @Test
    fun independent_first_open_under_different_catalogue_versions_leaves_each_devices_own_day_untouched() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = userId }
        val a = Device().also { it.open(fake) } // stays on version 1
        val b = Device().also { it.open(fake) }
        b.adoptSecondCatalogueVersion() // now also holds version 2, effective on `date`

        val planA = requireCreated(a.dayPlans.ensurePlanFor(date))
        val planB = requireCreated(b.dayPlans.ensurePlanFor(date))
        assertEquals(1, planA.catalogueVersion)
        assertEquals(2, planB.catalogueVersion)

        a.sync(); b.sync(); a.sync(); b.sync()

        // FR-024a: a day already materialised locally is never re-derived, re-versioned or rewritten.
        assertEquals("A's own stored day must not change", 1, requireNotNull(a.dayPlans.planFor(date)).catalogueVersion)
        assertEquals("B's own stored day must not change", 2, requireNotNull(b.dayPlans.planFor(date)).catalogueVersion)

        // Completions and earned points still agree even though the stored versions differ.
        assertEquals(
            a.completions.liveBetween(date, date).map { it.taskSlug }.sorted(),
            b.completions.liveBetween(date, date).map { it.taskSlug }.sorted(),
        )
        val earnedA = a.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        val earnedB = b.completions.liveBetween(date, date).sumOf { it.pointsAwarded }
        assertEquals(earnedA, earnedB)

        // The account settles on the lower of the two (FR-024b) — LEAST(catalogue_version) server-side.
        val (remoteDayRecords, _) = fake.rows()
        val remoteVersion = remoteDayRecords.single { it.date == date.toString() }.catalogueVersion
        assertEquals(1, remoteVersion)

        // A third device joining fresh derives that lower version.
        val c = Device().also { it.open(fake) }
        c.sync()
        val planC = c.dayPlans.planFor(date)
        assertEquals(1, planC?.catalogueVersion)

        a.close(); b.close(); c.close()
    }

    private fun requireCreated(outcome: EnsureOutcome) =
        (outcome as? EnsureOutcome.Created)?.plan ?: throw AssertionError("expected Created, got $outcome")

    private fun requireRecorded(outcome: RecordOutcome) =
        (outcome as? RecordOutcome.Recorded)?.completion ?: throw AssertionError("expected Recorded, got $outcome")

    private fun requireReversed(outcome: UndoOutcome) =
        (outcome as? UndoOutcome.Reversed)?.completion ?: throw AssertionError("expected Reversed, got $outcome")
}
