package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.RemoteResult
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoard
import com.giraffe.mizanapp.data.sync.dto.RemoteHonorBoardMember
import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SC-012: inspects everything the client can retrieve about the Honor Board,
 * not what it renders. No threshold, no distance to it, no non-qualifier
 * count, no per-person days figure, and no identity of anyone who did not
 * qualify — because the read model does not carry the data for any of it.
 */
class HonorBoardLeakTest {

    @Test
    fun the_dto_shape_carries_none_of_the_forbidden_fields() {
        val memberFields = RemoteHonorBoardMember::class.java.declaredFields.map { it.name }
        val boardFields = RemoteHonorBoard::class.java.declaredFields.map { it.name }
        val forbidden = listOf(
            "threshold", "thresholdDistance", "daysShort", "shortfall", "nonQualifierCount",
            "missedCount", "daysEngaged", "days_engaged", "points", "position", "email",
        )
        (memberFields + boardFields).forEach { field ->
            assertFalse("field \"$field\" must not exist on the Honor Board DTOs", forbidden.any { field.contains(it, ignoreCase = true) })
        }
    }

    @Test
    fun a_non_qualifiers_identity_never_appears_in_the_serialised_response() = runBlocking {
        val fake = FakeRemoteDataSource()
        fake.currentUserId = QUALIFIER
        fake.reportZone("Africa/Cairo")
        fake.setParticipation(true)
        fake.seedHonorBoard(PeriodKind.WEEKLY, REGION, listOf(RemoteHonorBoardMember("Qualifier", true)))

        val board = (fake.honorBoard(PeriodKind.WEEKLY) as RemoteResult.Ok).value
        val serialised = Json.encodeToString(board)

        assertTrue(serialised.contains("Qualifier"))
        assertFalse("no non-qualifier identity, count, or days figure may appear anywhere", serialised.contains(NON_QUALIFIER))
    }

    private companion object {
        const val QUALIFIER = "qualifier"
        const val NON_QUALIFIER = "Never Qualified"
        const val REGION = "egypt-cairo"
    }
}
