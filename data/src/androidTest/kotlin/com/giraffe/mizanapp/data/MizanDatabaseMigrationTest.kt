package com.giraffe.mizanapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MizanDatabase
import com.giraffe.mizanapp.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The 1 -> 2 migration adds `day_plans.origin` and nothing else.
 *
 * Additive by construction: no row is rewritten, no figure moves. The
 * default is not a guess — `002`'s only creation path is `ensurePlanFor`
 * called for the current date, so no plan in a v1 database can be a
 * backfill (FR-013e).
 */
class MizanDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MizanDatabase::class.java,
    )

    private companion object {
        const val DB_NAME = "migration-test.db"
    }

    @Test
    fun a_v1_days_plan_and_completions_survive_with_identical_values() {
        val v1: SupportSQLiteDatabase = helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO day_plans (id, date, catalogueVersion, hijriLabel, availablePoints, updatedAt) " +
                    "VALUES ('p1', '2026-08-08', 1, 'X', 69, 1)",
            )
            execSQL(
                "INSERT INTO completions (id, dayPlanId, taskSlug, creditedDate, pointsAwarded, recordedAt, updatedAt) " +
                    "VALUES ('c1', 'p1', 'fajr-1', '2026-08-08', 2, 1, 1)",
            )
        }
        v1.close()

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        migrated.query("SELECT availablePoints, hijriLabel, origin FROM day_plans WHERE id = 'p1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(69, cursor.getInt(cursor.getColumnIndexOrThrow("availablePoints")))
            assertEquals("X", cursor.getString(cursor.getColumnIndexOrThrow("hijriLabel")))
            assertEquals("OPENED", cursor.getString(cursor.getColumnIndexOrThrow("origin")))
        }

        migrated.query("SELECT pointsAwarded FROM completions WHERE id = 'c1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("pointsAwarded")))
        }
    }
}
