package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SC-010 — the Principle III test this increment owes. A closed period's
 * standings and Honor Board membership must be byte-identical after every
 * mutating path this increment has: a re-priced completion, a reversal, a
 * late completion landing inside the closed window, and a member opting out.
 */
class ClosedPeriodImmutabilityTest {

    @Test
    fun a_closed_periods_standings_and_honor_board_survive_every_mutating_path() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        optIn(fake, BOB)
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 5, "2026-08-17T09:00:00Z")))
        fake.upsertCompletions(listOf(completion("b1", BOB, "2026-08-17", 3, "2026-08-17T09:00:00Z")))
        fake.recomputeOpenPeriods()
        fake.seedHonorBoard(
            PeriodKind.WEEKLY,
            REGION,
            listOf(RemoteHonorBoardMember("Alice", false)),
            periodStart = "2026-08-15",
            memberUserIds = listOf(ALICE),
        )
        fake.markPeriodClosed(PeriodKind.WEEKLY, REGION)

        fake.currentUserId = BOB
        val rankingBefore = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)
        val honorBefore = Json.encodeToString((fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value)

        // 1. A "catalogue re-price" landing on an already-recorded completion.
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 999, "2026-08-17T09:00:00Z")))
        // 2. Reversing a completion that was inside the closed period.
        fake.upsertCompletions(
            listOf(completion("a1", ALICE, "2026-08-17", 5, "2026-08-17T09:00:00Z", reversedAt = "2026-08-18T00:00:00Z")),
        )
        // 3. A late completion for a date inside the closed window.
        fake.upsertCompletions(listOf(completion("a2", ALICE, "2026-08-16", 50, "2026-08-20T00:00:00Z")))
        fake.recomputeOpenPeriods()
        // 4. A member of the closed board opting out.
        fake.currentUserId = ALICE
        fake.setParticipation(false)

        fake.currentUserId = BOB
        val rankingAfter = Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value)
        val honorAfter = Json.encodeToString((fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value)

        assertEquals("closed standings must not move", rankingBefore, rankingAfter)
        assertEquals("closed Honor Board membership must not move", honorBefore, honorAfter)
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private fun completion(
        id: String,
        userId: String,
        creditedDate: String,
        points: Int,
        recordedAt: String,
        reversedAt: String? = null,
    ) = RemoteCompletion(
        id = id,
        userId = userId,
        creditedDate = creditedDate,
        taskSlug = "task",
        pointsAwarded = points,
        recordedAt = recordedAt,
        reversedAt = reversedAt,
    )

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val REGION = "egypt-cairo"
    }
}
