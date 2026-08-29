package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RoomParticipationRepository
import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingPage
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Withdrawal mutates every open period and cannot reach a closed snapshot. */
class ParticipationWithdrawalTest : DbTestBase() {
    @Test
    fun withdrawal_removes_open_and_future_entries_but_closed_outputs_are_byte_identical() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, VIEWER)
        optIn(fake, OBSERVER)
        val candidates = listOf(entry(VIEWER, 20, 1), entry(OBSERVER, 10, 2))
        fake.seedEntries(PeriodKind.DAILY, REGION, candidates, periodStart = "2026-03-14")
        fake.seedEntries(PeriodKind.WEEKLY, REGION, candidates, periodStart = "2026-03-14")
        fake.seedHonorBoard(
            PeriodKind.WEEKLY,
            REGION,
            listOf(RemoteHonorBoardMember("Viewer", true), RemoteHonorBoardMember("Observer", false)),
            periodStart = "2026-03-14",
        )
        fake.markPeriodClosed(PeriodKind.WEEKLY, REGION)

        fake.currentUserId = OBSERVER
        val closedRankingBefore = Json.encodeToString(page(fake, PeriodKind.WEEKLY))
        val closedHonorBefore = Json.encodeToString((fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value)

        fake.currentUserId = VIEWER
        val repository = RoomParticipationRepository(db, fake)
        repository.optIn(ZoneId.of("Africa/Cairo"))
        assertEquals(ParticipationResult.Applied, repository.optOut())

        fake.currentUserId = OBSERVER
        assertFalse(page(fake, PeriodKind.DAILY).entries.any { it.userId == VIEWER })
        assertEquals(closedRankingBefore, Json.encodeToString(page(fake, PeriodKind.WEEKLY)))
        assertEquals(
            closedHonorBefore,
            Json.encodeToString((fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value),
        )

        fake.seedNewOpenPeriod(PeriodKind.MONTHLY, REGION, candidates, periodStart = "2026-04-01")
        assertFalse(page(fake, PeriodKind.MONTHLY).entries.any { it.userId == VIEWER })
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private suspend fun page(fake: FakeRemoteDataSource, kind: PeriodKind): RemoteRankingPage =
        (fake.rankingPage(kind, null) as RemoteResult.Ok).value

    private fun entry(userId: String, points: Int, position: Int) = RemoteRankingEntry(
        userId = userId,
        displayName = if (userId == VIEWER) "Viewer" else "Observer",
        points = points,
        position = position,
    )

    private companion object {
        const val VIEWER = "viewer"
        const val OBSERVER = "observer"
        const val REGION = "egypt-cairo"
    }
}
