package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-027, FR-004a: exactly the qualifying members appear, and only an open board loses one on opt-out. */
class HonorBoardQualificationTest {

    @Test
    fun exactly_those_at_or_above_the_threshold_appear() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        fake.seedHonorBoard(PeriodKind.WEEKLY, REGION, listOf(RemoteHonorBoardMember("Alice", true)))

        val board = (fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value
        assertTrue(board.members.any { it.displayName == "Alice" })
        assertFalse(board.members.any { it.displayName == "Bob" })
    }

    @Test
    fun opting_out_removes_a_member_from_an_open_board_but_a_closed_one_survives() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, VIEWER)
        optIn(fake, OBSERVER)
        fake.seedHonorBoard(
            PeriodKind.WEEKLY,
            REGION,
            listOf(RemoteHonorBoardMember("Viewer", false), RemoteHonorBoardMember("Observer", false)),
            periodStart = "2026-08-15",
        )
        fake.seedHonorBoard(PeriodKind.MONTHLY, REGION, listOf(RemoteHonorBoardMember("Viewer", false)), periodStart = "2026-08-01")
        fake.markPeriodClosed(PeriodKind.MONTHLY, REGION)

        fake.currentUserId = VIEWER
        fake.setParticipation(false)

        fake.currentUserId = OBSERVER
        val weekly = (fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value
        assertFalse("the open board must lose the withdrawn member", weekly.members.any { it.displayName == "Viewer" })
        assertTrue(weekly.members.any { it.displayName == "Observer" })

        val monthly = (fake.honorBoard(PeriodKind.MONTHLY) as RemoteResult.Ok).value
        assertTrue("a closed board's membership must survive withdrawal (FR-004a)", monthly.members.any { it.displayName == "Viewer" })
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private companion object {
        const val ALICE = "alice"
        const val VIEWER = "viewer"
        const val OBSERVER = "observer"
        const val REGION = "egypt-cairo"
    }
}
