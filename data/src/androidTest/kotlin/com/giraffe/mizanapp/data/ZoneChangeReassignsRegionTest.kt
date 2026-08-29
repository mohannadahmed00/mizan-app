package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RoomParticipationRepository
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import com.giraffe.mizanapp.domain.time.DayBoundary
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-012, FR-013: a device that changes zone is reassigned, and a closed period never moves. */
class ZoneChangeReassignsRegionTest : DbTestBase() {

    @Test
    fun changing_the_device_zone_reassigns_the_region_without_touching_a_closed_period() = runBlocking {
        val fake = FakeRemoteDataSource()
        fake.currentUserId = VIEWER
        val repository = RoomParticipationRepository(db, fake)
        repository.optIn(ZoneId.of("Africa/Cairo"))
        assertEquals("egypt-cairo", repository.observe().first().region?.id?.value)

        // Both participants in Cairo; close a weekly period there before the move.
        fake.currentUserId = OBSERVER
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
        fake.seedEntries(PeriodKind.WEEKLY, "egypt-cairo", listOf(RemoteRankingEntry(VIEWER, "Viewer", 10, 1)))
        fake.markPeriodClosed(PeriodKind.WEEKLY, "egypt-cairo")
        val closedBefore = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)

        // The device travels to a zone mapped to a different region.
        time.setZone(ZoneId.of("Asia/Karachi"))
        fake.currentUserId = VIEWER
        val outcome = repository.reportZone(time.zone())

        assertEquals(ParticipationResult.Applied, outcome)
        val afterReassignment = repository.observe().first()
        assertEquals("pakistan-karachi", afterReassignment.region?.id?.value)
        assertEquals(ZoneId.of("Asia/Karachi"), afterReassignment.region?.zone)
        assertEquals(DayBoundary.dateAt(time.now(), time.zone()), time.today())

        fake.currentUserId = OBSERVER
        val closedAfter = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)
        assertEquals("the closed Cairo ranking must be byte-identical after the move", closedBefore, closedAfter)
    }

    private companion object {
        const val VIEWER = "viewer"
        const val OBSERVER = "observer"
    }
}
