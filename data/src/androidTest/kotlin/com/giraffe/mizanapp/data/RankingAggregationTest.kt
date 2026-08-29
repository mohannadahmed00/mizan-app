package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-004, FR-021, FR-022: every published total and position matches an
 * independent hand-computation over non-reversed completions in the region's
 * timezone, a catalogue re-price never moves a past total (Principle III),
 * and the tie-break is exactly "who reached the total earliest" — bounded by
 * FR-022a so a forged clock can reorder a tie and nothing more.
 */
class RankingAggregationTest {

    @Test
    fun weekly_total_matches_an_independent_hand_computation_over_non_reversed_completions() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        optIn(fake, BOB)

        fake.upsertCompletions(
            listOf(
                completion("a1", ALICE, "2026-08-17", 5, "2026-08-17T09:00:00Z"),
                completion("a2", ALICE, "2026-08-18", 7, "2026-08-18T09:00:00Z"),
                // Reversed: must be excluded from the total (FR-021).
                completion("a3", ALICE, "2026-08-19", 100, "2026-08-19T09:00:00Z", reversedAt = "2026-08-19T10:00:00Z"),
            ),
        )
        fake.upsertCompletions(listOf(completion("b1", BOB, "2026-08-17", 4, "2026-08-17T09:00:00Z")))

        fake.recomputeOpenPeriods()

        val alice = entryFor(fake, ALICE)
        val bob = entryFor(fake, BOB)
        assertEquals("the reversed completion must not count", 12, alice.points)
        assertEquals(4, bob.points)
        assertTrue("the higher total ranks first", alice.position < bob.position)
    }

    @Test
    fun a_catalogue_points_change_never_moves_a_past_total() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 5, "2026-08-17T09:00:00Z")))
        fake.recomputeOpenPeriods()
        val before = entryFor(fake, ALICE).points

        // Re-upserting the same completion id with a different points value simulates a
        // later catalogue re-price landing on an already-recorded row. The write layer
        // freezes every field but reversedAt on the first write, so this must not move it.
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 999, "2026-08-17T09:00:00Z")))
        fake.recomputeOpenPeriods()
        val after = entryFor(fake, ALICE).points

        assertEquals(before, after)
    }

    @Test
    fun the_tie_break_is_whoever_reached_the_total_earliest() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        optIn(fake, BOB)
        // Equal totals; Alice's completion lands earlier than Bob's.
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 10, "2026-08-17T08:00:00Z")))
        fake.upsertCompletions(listOf(completion("b1", BOB, "2026-08-17", 10, "2026-08-17T09:00:00Z")))

        fake.recomputeOpenPeriods()

        val alice = entryFor(fake, ALICE)
        val bob = entryFor(fake, BOB)
        assertEquals(alice.points, bob.points)
        assertTrue("the earlier last completion wins the tie", alice.position < bob.position)
    }

    /** FR-022a: a forged clock may reorder a tie and nothing else. */
    @Test
    fun a_forged_recorded_at_may_reorder_a_tie_but_changes_nothing_else() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        optIn(fake, BOB)
        fake.upsertCompletions(listOf(completion("a1", ALICE, "2026-08-17", 8, "2026-08-17T09:00:00Z")))
        fake.upsertCompletions(listOf(completion("b1", BOB, "2026-08-17", 20, "2026-08-17T09:00:00Z")))
        fake.recomputeOpenPeriods()
        val alicePointsBefore = entryFor(fake, ALICE).points
        val daysEngagedBefore = fake.daysEngagedFor(PeriodKind.WEEKLY, REGION, ALICE)

        // Replay with a recorded_at forged far into the past — no new points, no new day.
        fake.upsertCompletions(listOf(completion("a2", ALICE, "2026-08-17", 0, "1970-01-01T00:00:00Z")))
        fake.recomputeOpenPeriods()

        val aliceAfter = entryFor(fake, ALICE)
        val bobAfter = entryFor(fake, BOB)
        assertEquals("a forged clock must not move the points total", alicePointsBefore, aliceAfter.points)
        assertEquals(
            "a forged clock must not move days_engaged",
            daysEngagedBefore,
            fake.daysEngagedFor(PeriodKind.WEEKLY, REGION, ALICE),
        )
        assertTrue("still below whoever has a higher total, forged clock or not", aliceAfter.position > bobAfter.position)
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private suspend fun entryFor(fake: FakeRemoteDataSource, userId: String): RemoteRankingEntry {
        fake.currentUserId = userId
        val page = (fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value
        val entry = page.entries.firstOrNull { it.userId == userId }
        assertNotNull("$userId must appear in the weekly ranking", entry)
        return entry!!
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
