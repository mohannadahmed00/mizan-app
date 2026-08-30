package com.giraffe.mizanapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MIGRATION_4_5
import com.giraffe.mizanapp.data.db.MizanDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BoundaryStateMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MizanDatabase::class.java,
    )

    @Test
    fun migrationAddsBoundaryStateWithoutChangingRecordedHistory() {
        helper.createDatabase(DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO day_plans " +
                    "(id, date, catalogueVersion, hijriLabel, availablePoints, updatedAt, deletedAt, userId, origin, syncedAt) " +
                    "VALUES ('plan-1', '2026-03-13', 7, '12 Ramadan 1447', 42, 101, NULL, 'user-1', 'OPENED', 102)",
            )
            execSQL(
                "INSERT INTO completions " +
                    "(id, dayPlanId, taskSlug, creditedDate, pointsAwarded, recordedAt, reversedAt, updatedAt, deletedAt, userId, syncedAt) " +
                    "VALUES ('completion-1', 'plan-1', 'maghrib', '2026-03-13', 5, 103, NULL, 104, NULL, 'user-1', 105)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)
        assertRow(
            db,
            "day_plans",
            linkedMapOf(
                "id" to "plan-1", "date" to "2026-03-13", "catalogueVersion" to "7",
                "hijriLabel" to "12 Ramadan 1447", "availablePoints" to "42", "updatedAt" to "101",
                "deletedAt" to null, "userId" to "user-1", "origin" to "OPENED", "syncedAt" to "102",
            ),
        )
        assertRow(
            db,
            "completions",
            linkedMapOf(
                "id" to "completion-1", "dayPlanId" to "plan-1", "taskSlug" to "maghrib",
                "creditedDate" to "2026-03-13", "pointsAwarded" to "5", "recordedAt" to "103",
                "reversedAt" to null, "updatedAt" to "104", "deletedAt" to null, "userId" to "user-1",
                "syncedAt" to "105",
            ),
        )
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'boundary_state'").use {
            assertTrue("boundary_state must exist after migration", it.moveToFirst())
        }
    }

    private fun assertRow(
        database: SupportSQLiteDatabase,
        table: String,
        expected: LinkedHashMap<String, String?>,
    ) {
        database.query("SELECT ${expected.keys.joinToString()} FROM $table").use { cursor ->
            assertTrue("$table must retain its row", cursor.moveToFirst())
            expected.entries.forEachIndexed { index, entry ->
                assertEquals("$table.${entry.key} changed", entry.value, cursor.getString(index))
            }
            assertEquals("$table must contain one row", false, cursor.moveToNext())
        }
    }

    private companion object {
        const val DB_NAME = "boundary-state-migration.db"
    }
}
