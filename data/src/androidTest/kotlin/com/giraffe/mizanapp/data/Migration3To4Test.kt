package com.giraffe.mizanapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MIGRATION_3_4
import com.giraffe.mizanapp.data.db.MizanDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The disposable leaderboard tables must arrive without rewriting any record
 * that contributes to the user's history or pending sync state.
 */
class Migration3To4Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MizanDatabase::class.java,
    )

    @Test
    fun migration_preserves_all_existing_rows_and_adds_two_empty_tables() {
        helper.createDatabase(DB_NAME, 3).apply {
            execSQL(
                "INSERT INTO day_plans " +
                    "(id, date, catalogueVersion, hijriLabel, availablePoints, updatedAt, deletedAt, " +
                    "userId, origin, syncedAt) VALUES " +
                    "('plan-1', '2026-08-29', 7, '16 Rabi al-Awwal 1448', 69, 101, NULL, " +
                    "'user-1', 'OPENED', 102)",
            )
            execSQL(
                "INSERT INTO planned_tasks " +
                    "(id, dayPlanId, taskSlug, sectionId, sectionLabel, sectionOrder, displayPosition, " +
                    "label, points, maxOccurrencesPerDay, updatedAt, deletedAt, userId) VALUES " +
                    "('task-1', 'plan-1', 'fajr', 'prayer', 'Prayer', 1, 2, 'Fajr', 5, 1, 103, " +
                    "NULL, 'user-1')",
            )
            execSQL(
                "INSERT INTO completions " +
                    "(id, dayPlanId, taskSlug, creditedDate, pointsAwarded, recordedAt, reversedAt, " +
                    "updatedAt, deletedAt, userId, syncedAt) VALUES " +
                    "('completion-1', 'plan-1', 'fajr', '2026-08-29', 5, 104, NULL, 105, NULL, " +
                    "'user-1', 106)",
            )
            execSQL(
                "INSERT INTO outbox " +
                    "(id, entityType, entityId, operation, payload, createdAt, attempts, nextAttemptAt) " +
                    "VALUES ('completion:completion-1:UPSERT', 'COMPLETION', 'completion-1', " +
                    "'UPSERT', '{\"id\":\"completion-1\"}', 107, 2, 108)",
            )
            execSQL("INSERT INTO sync_cursors (`key`, value) VALUES ('pull_cursor', 'cursor-1')")
            execSQL(
                "INSERT INTO account_scope (id, userId, email, displayName, updatedAt) VALUES " +
                    "(0, 'user-1', 'person@example.test', 'Person', 109)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

        assertRow(
            migrated,
            "day_plans",
            linkedMapOf(
                "id" to "plan-1",
                "date" to "2026-08-29",
                "catalogueVersion" to "7",
                "hijriLabel" to "16 Rabi al-Awwal 1448",
                "availablePoints" to "69",
                "updatedAt" to "101",
                "deletedAt" to null,
                "userId" to "user-1",
                "origin" to "OPENED",
                "syncedAt" to "102",
            ),
        )
        assertRow(
            migrated,
            "planned_tasks",
            linkedMapOf(
                "id" to "task-1",
                "dayPlanId" to "plan-1",
                "taskSlug" to "fajr",
                "sectionId" to "prayer",
                "sectionLabel" to "Prayer",
                "sectionOrder" to "1",
                "displayPosition" to "2",
                "label" to "Fajr",
                "points" to "5",
                "maxOccurrencesPerDay" to "1",
                "updatedAt" to "103",
                "deletedAt" to null,
                "userId" to "user-1",
            ),
        )
        assertRow(
            migrated,
            "completions",
            linkedMapOf(
                "id" to "completion-1",
                "dayPlanId" to "plan-1",
                "taskSlug" to "fajr",
                "creditedDate" to "2026-08-29",
                "pointsAwarded" to "5",
                "recordedAt" to "104",
                "reversedAt" to null,
                "updatedAt" to "105",
                "deletedAt" to null,
                "userId" to "user-1",
                "syncedAt" to "106",
            ),
        )
        assertRow(
            migrated,
            "outbox",
            linkedMapOf(
                "id" to "completion:completion-1:UPSERT",
                "entityType" to "COMPLETION",
                "entityId" to "completion-1",
                "operation" to "UPSERT",
                "payload" to "{\"id\":\"completion-1\"}",
                "createdAt" to "107",
                "attempts" to "2",
                "nextAttemptAt" to "108",
            ),
        )
        assertRow(migrated, "sync_cursors", linkedMapOf("key" to "pull_cursor", "value" to "cursor-1"))
        assertRow(
            migrated,
            "account_scope",
            linkedMapOf(
                "id" to "0",
                "userId" to "user-1",
                "email" to "person@example.test",
                "displayName" to "Person",
                "updatedAt" to "109",
            ),
        )
        assertEmpty(migrated, "leaderboard_cache")
        assertEmpty(migrated, "participation_state")
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

    private fun assertEmpty(database: SupportSQLiteDatabase, table: String) {
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$table must start empty", 0, cursor.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-3-to-4-test.db"
    }
}
