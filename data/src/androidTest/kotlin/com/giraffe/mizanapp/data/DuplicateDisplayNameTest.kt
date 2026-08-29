package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-017, FR-007a: duplicate display names are not disambiguated — each participant still finds their own row. */
class DuplicateDisplayNameTest {

    @Test
    fun two_participants_sharing_a_display_name_are_both_listed_unaltered() = runBlocking {
        val fake = FakeRemoteDataSource()
        optIn(fake, ALICE)
        optIn(fake, BOB)
        fake.seedEntries(
            PeriodKind.WEEKLY,
            REGION,
            listOf(RemoteRankingEntry(ALICE, "Person", 10, 1), RemoteRankingEntry(BOB, "Person", 5, 2)),
        )

        fake.currentUserId = ALICE
        val aliceView = (fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value
        assertEquals("both duplicate-named entries must be present", 2, aliceView.entries.count { it.displayName == "Person" })
        assertTrue("Alice must find her own row by user id", aliceView.entries.any { it.userId == ALICE })

        fake.currentUserId = BOB
        val bobView = (fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value
        assertTrue("Bob must find his own row by user id", bobView.entries.any { it.userId == BOB })
        assertTrue("neither name may be altered or suffixed", bobView.entries.all { it.displayName == "Person" })
    }

    private suspend fun optIn(fake: FakeRemoteDataSource, userId: String) {
        fake.currentUserId = userId
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        const val REGION = "egypt-cairo"
    }
}
