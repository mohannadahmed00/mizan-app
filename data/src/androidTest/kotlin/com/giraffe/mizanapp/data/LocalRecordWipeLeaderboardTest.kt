package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.db.entity.LeaderboardCacheEntity
import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import com.giraffe.mizanapp.data.sync.LocalRecordWipe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Account removal must clear disposable social state without removing fixed content. */
class LocalRecordWipeLeaderboardTest : DbTestBase() {

    @Test
    fun wipe_clears_leaderboard_state_and_preserves_catalogue_tables() = runBlocking {
        catalogue.seedIfNeeded()
        db.leaderboardCacheDao().upsert(
            LeaderboardCacheEntity(
                id = "WEEKLY:2026-08-29:egypt-cairo",
                periodKind = "WEEKLY",
                periodStart = "2026-08-29",
                regionId = "egypt-cairo",
                regionDisplayName = "Egypt (Cairo)",
                payload = "{\"entries\":[]}",
                retrievedAt = 100L,
            ),
        )
        db.participationStateDao().upsert(
            ParticipationStateEntity(
                optedIn = true,
                regionId = "egypt-cairo",
                regionDisplayName = "Egypt (Cairo)",
                updatedAt = 101L,
            ),
        )

        LocalRecordWipe(db).wipe()

        assertEquals(0, rowCount("leaderboard_cache"))
        assertEquals(0, rowCount("participation_state"))
        assertTrue(rowCount("sections") > 0)
        assertTrue(rowCount("task_definitions") > 0)
        assertTrue(rowCount("catalogue_versions") > 0)
        assertTrue(rowCount("task_versions") > 0)
    }

    private fun rowCount(table: String): Int = db.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
}
