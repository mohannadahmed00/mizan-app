package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RoomParticipationRepository
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consent starts off and reports only the device zone for server assignment. */
class ParticipationOptInTest : DbTestBase() {

    @Test
    fun fresh_account_is_off_then_opt_in_reports_zone_and_stores_assigned_region() = runBlocking {
        val fake = FakeRemoteDataSource().apply { currentUserId = "user-1" }
        val repository = RoomParticipationRepository(db, fake)

        val initial = repository.observe().first()
        assertFalse(initial.optedIn)
        assertNull(initial.region)

        val result = repository.optIn(ZoneId.of("Africa/Cairo"))

        assertEquals(ParticipationResult.Applied, result)
        assertEquals(listOf("Africa/Cairo"), fake.reportedZones)
        val stored = repository.observe().first()
        assertTrue(stored.optedIn)
        assertEquals("egypt-cairo", stored.region?.id?.value)
        assertEquals("Egypt (Cairo)", stored.region?.displayName)
        assertEquals(ZoneId.of("Africa/Cairo"), stored.region?.zone)
    }
}
