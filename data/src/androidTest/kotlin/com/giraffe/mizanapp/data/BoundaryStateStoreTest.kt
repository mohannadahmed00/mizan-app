package com.giraffe.mizanapp.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import com.giraffe.mizanapp.data.prayer.FakeLocationSource
import com.giraffe.mizanapp.data.prayer.FakePrayerTimes
import com.giraffe.mizanapp.data.time.BoundaryStateStore
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.FallbackReason
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    @Test
    fun dateAdvancesAtMaghribAndNotAtMidnight() = runTest {
        val day = LocalDate.of(2026, 3, 14)
        storeCoordinates()
        prayerTimes.setMaghrib(day, Instant.parse("2026-03-14T16:00:00Z")) // 18:00 Cairo
        prayerTimes.setMaghrib(day.plusDays(1), Instant.parse("2026-03-15T16:00:00Z"))

        store.refresh(Instant.parse("2026-03-14T15:59:00Z"), zone) // 17:59 Cairo
        assertEquals(day, store.current().resolvedDate)

        store.refresh(Instant.parse("2026-03-14T16:01:00Z"), zone) // 18:01 Cairo
        assertEquals(day.plusDays(1), store.current().resolvedDate)

        store.refresh(Instant.parse("2026-03-14T22:30:00Z"), zone) // 00:30 Cairo, past local midnight
        assertEquals(day.plusDays(1), store.current().resolvedDate)
    }

    @Test
    fun refreshingBeforeExpiresAtChangesNothing() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")
        store.refresh(now, zone)
        val afterFirst = store.current()

        store.refresh(now.plusSeconds(60), zone)
        val afterSecond = store.current()

        assertEquals(afterFirst.resolvedDate, afterSecond.resolvedDate)
        assertEquals(afterFirst.expiresAt, afterSecond.expiresAt)
    }

    @Test
    fun refreshingPastExpiresAtAdvancesTheDateAndMovesExpiresAt() = runTest {
        val now = Instant.parse("2026-03-14T09:00:00Z")
        store.refresh(now, zone)
        val before = store.current()

        store.refresh(before.expiresAt.plusSeconds(60), zone)
        val after = store.current()

        assertTrue(after.resolvedDate.isAfter(before.resolvedDate))
        assertTrue(after.expiresAt.isAfter(before.expiresAt))
    }

    @Test
    fun freshInstallWithNoLocationUsesTheFallbackRegime() = runTest {
        store.refresh(Instant.parse("2026-03-14T09:00:00Z"), zone)
        assertTrue(store.current().regime is BoundaryRegime.Fallback)
    }

    @Test
    fun fallbackReasonIsNeverHadLocationOnAFreshInstall() = runTest {
        store.refresh(Instant.parse("2026-03-14T09:00:00Z"), zone)
        val regime = store.current().regime as BoundaryRegime.Fallback
        assertEquals(FallbackReason.NEVER_HAD_LOCATION, regime.reason)
    }

    @Test
    fun currentReturnsImmediatelyWithNoCoordinates() {
        // No runTest, no refresh: current() must be a plain, non-suspending field read.
        val state = store.current()
        assertTrue(state.regime is BoundaryRegime.Fallback)
    }

    @Test
    fun ninetyOfflineDaysWithAnUnchangedZoneKeepTheMaghribRegime() = runTest {
        storeCoordinates()
        prayerTimes.setDefaultMaghribLocalTime(LocalTime.of(18, 0))

        var now = Instant.parse("2026-03-14T09:00:00Z")
        repeat(90) {
            store.refresh(now, zone)
            assertEquals(BoundaryRegime.Maghrib, store.current().regime)
            now = now.plus(Duration.ofDays(1))
        }
    }

    @Test
    fun aZoneIdChangeWithNoFreshFixMovesToTheFallback() = runTest {
        storeCoordinates(zoneId = "Africa/Cairo")
        locationSource.setCoordinates(null)

        store.refresh(Instant.parse("2026-03-14T09:00:00Z"), ZoneId.of("Asia/Riyadh"))

        val regime = store.current().regime as BoundaryRegime.Fallback
        assertEquals(FallbackReason.ZONE_CHANGED_AWAITING_FIX, regime.reason)
    }

    @Test
    fun aDaylightSavingOffsetChangeDoesNotInvalidateCoordinates() = runTest {
        storeCoordinates(zoneId = "Africa/Cairo")
        prayerTimes.setDefaultMaghribLocalTime(LocalTime.of(18, 0))

        store.refresh(Instant.parse("2026-01-14T09:00:00Z"), zone) // winter
        assertEquals(BoundaryRegime.Maghrib, store.current().regime)

        store.refresh(Instant.parse("2026-07-14T09:00:00Z"), zone) // summer
        assertEquals(BoundaryRegime.Maghrib, store.current().regime)
    }

    @Test
    fun aFreshFixAfterAZoneChangeResumesTheMaghribRegime() = runTest {
        storeCoordinates(zoneId = "Africa/Cairo")
        val riyadh = ZoneId.of("Asia/Riyadh")
        locationSource.setCoordinates(Coordinates(24.7, 46.7))
        prayerTimes.setDefaultMaghribLocalTime(LocalTime.of(18, 0))

        store.refresh(Instant.parse("2026-03-14T09:00:00Z"), riyadh)

        assertEquals(BoundaryRegime.Maghrib, store.current().regime)
    }

    private companion object {
        const val TEST_DB = "boundary-state-test.db"
    }
}
