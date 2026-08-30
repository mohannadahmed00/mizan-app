package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoard
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.data.sync.dto.RemoteOwnRank
import com.giraffe.mizanapp.data.sync.dto.RemoteParticipation
import com.giraffe.mizanapp.data.sync.dto.RemoteProfile
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingEntry
import com.giraffe.mizanapp.data.sync.dto.RemoteRankingPage
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * FR-006 — the highest-consequence privacy rule in this increment. Account A's
 * email must appear in none of the surfaces account B can retrieve, and no
 * leaderboard DTO may ever carry a field named `email`, even one added later.
 */
class NoEmailExposureTest {

    @Test
    fun no_leaderboard_dto_declares_a_field_named_email() {
        val classes = listOf(
            RemoteRankingPage::class.java,
            RemoteRankingEntry::class.java,
            RemoteOwnRank::class.java,
            RemoteHonorBoard::class.java,
            RemoteHonorBoardMember::class.java,
            RemoteParticipation::class.java,
        )
        classes.forEach { klass ->
            klass.declaredFields.forEach { field ->
                assertFalse(
                    "${klass.simpleName}.${field.name} must not be named after an email",
                    field.name.contains("email", ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun account_bs_view_of_every_surface_never_contains_account_as_email() = runBlocking {
        val fake = FakeRemoteDataSource()
        fake.currentUserId = ACCOUNT_A
        fake.upsertProfile(RemoteProfile(id = ACCOUNT_A, displayName = "Person A"))
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)

        fake.currentUserId = ACCOUNT_B
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
        fake.seedEntries(PeriodKind.WEEKLY, REGION, listOf(RemoteRankingEntry(ACCOUNT_A, "Person A", 10, 1)))
        fake.seedHonorBoard(PeriodKind.WEEKLY, REGION, listOf(RemoteHonorBoardMember("Person A", false)))

        val surfaces = listOf(
            Json.encodeToString((fake.rankingPage(PeriodKind.WEEKLY, null) as RemoteResult.Ok).value),
            Json.encodeToString((fake.ownRank(PeriodKind.WEEKLY) as RemoteResult.Ok).value),
            Json.encodeToString((fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value),
            Json.encodeToString((fake.setParticipation(true) as RemoteResult.Ok).value),
        )

        surfaces.forEach { serialised ->
            assertFalse("$serialised must not contain account A's email", serialised.contains(EMAIL_A))
        }
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        const val EMAIL_A = "a@example.test"
        const val REGION = "egypt-cairo"
    }
}
