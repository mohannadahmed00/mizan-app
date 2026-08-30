package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteCompletion
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** SC-015, FR-021a: opting in mid-period publishes the whole period's total, not a partial one. */
class MidPeriodOptInTest {

    @Test
    fun opting_in_on_the_last_day_of_the_period_publishes_every_earlier_day_too() = runBlocking {
        val fake = FakeRemoteDataSource()
        fake.currentUserId = ALICE

        // Recorded across the week before ever opting in.
        fake.upsertCompletions(
            listOf(
                completion("a1", "2026-08-15", 5, "2026-08-15T09:00:00Z"),
                completion("a2", "2026-08-16", 5, "2026-08-16T09:00:00Z"),
                // Opts in only on this, the week's last day.
                completion("a3", "2026-08-21", 5, "2026-08-21T09:00:00Z"),
            ),
        )
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)

        fake.recomputeOpenPeriods()

        val page = (fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value
        val entry = page.entries.single { it.userId == ALICE }
        assertEquals("the whole period counts, not only days after opting in", 15, entry.points)
    }

    private fun completion(id: String, creditedDate: String, points: Int, recordedAt: String) = RemoteCompletion(
        id = id,
        userId = ALICE,
        creditedDate = creditedDate,
        taskSlug = "task",
        pointsAwarded = points,
        recordedAt = recordedAt,
    )

    private companion object {
        const val ALICE = "alice"
    }
}
