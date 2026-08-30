package com.giraffe.mizanapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import com.giraffe.mizanapp.data.prayer.FakeLocationSource
import com.giraffe.mizanapp.data.prayer.FakePrayerTimes
import com.giraffe.mizanapp.data.time.BoundaryStateStore
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BoundaryStateStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Africa/Cairo")

    private lateinit var db: MizanDatabase
    private lateinit var locationSource: FakeLocationSource
    private lateinit var prayerTimes: FakePrayerTimes
    private lateinit var store: BoundaryStateStore

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(context, MizanDatabase::class.java, TEST_DB).build()
        locationSource = FakeLocationSource()
        prayerTimes = FakePrayerTimes()
        store = BoundaryStateStore(db.boundaryStateDao(), locationSource, prayerTimes)
    }

    private suspend fun storeCoordinates(zoneId: String = zone.id) {
        db.boundaryStateDao().upsert(
            BoundaryStateEntity(
                latitude = 30.0,
                longitude = 31.2,
                zoneIdWhenObtained = zoneId,
                obtainedAt = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli(),
                lastResolvedDate = null,
                lastResolvedRegime = null,
            ),
        )
    }

    @Test
    fun freshDatabaseResolvesADateWithNoCoordinates() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")
        store.refresh(now, zone)
        val state = store.current()
        assertTrue(state.regime is BoundaryRegime.Fallback)
        assertEquals(LocalDate.of(2026, 3, 14), state.resolvedDate)
    }

    @Test
    fun storingCoordinatesAndRefreshingMovesTheRegimeToMaghrib() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")
        storeCoordinates()
        prayerTimes.setMaghrib(LocalDate.of(2026, 3, 14), Instant.parse("2026-03-14T16:00:00Z"))

        store.refresh(now, zone)

        assertEquals(BoundaryRegime.Maghrib, store.current().regime)
    }

    @Test
    fun resolvedDateAndExpiresAtArePopulatedInBothCases() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")

        store.refresh(now, zone)
        assertFalse(store.current().expiresAt.isBefore(now))

        openDatabase()
        storeCoordinates()
        prayerTimes.setMaghrib(LocalDate.of(2026, 3, 14), Instant.parse("2026-03-14T16:00:00Z"))
        prayerTimes.setMaghrib(LocalDate.of(2026, 3, 15), Instant.parse("2026-03-15T16:00:00Z"))
        store.refresh(now, zone)
        assertFalse(store.current().expiresAt.isBefore(now))
    }

    @Test
    fun lastResolvedDateIsPersistedAcrossAStoreReload() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")
        store.refresh(now, zone)
        val resolvedBeforeReload = store.current().resolvedDate

        store = BoundaryStateStore(db.boundaryStateDao(), locationSource, prayerTimes)
        val persisted = db.boundaryStateDao().get()

        assertEquals(resolvedBeforeReload.toString(), persisted?.lastResolvedDate)
    }

    private companion object {
        const val TEST_DB = "boundary-state-test.db"
    }
}
