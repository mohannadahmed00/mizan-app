package com.giraffe.mizanapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.giraffe.mizanapp.data.db.MIGRATION_5_6
import com.giraffe.mizanapp.data.db.MizanDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MizanDatabase::class.java)
    @Test fun migration_preserves_history_and_adds_only_notification_tables() {
        val name = "notification-migration.db"
        val v5: SupportSQLiteDatabase = helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO day_plans (id,date,catalogueVersion,hijriLabel,availablePoints,updatedAt,origin) VALUES ('p','2026-09-04',1,'H',42,7,'OPENED')")
            execSQL("INSERT INTO completions (id,dayPlanId,taskSlug,creditedDate,pointsAwarded,recordedAt,updatedAt) VALUES ('c','p','fajr-1','2026-09-04',2,3,4)")
        }
        v5.close()
        val migrated = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)
        migrated.query("SELECT availablePoints, hijriLabel FROM day_plans WHERE id='p'").use { assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)); assertEquals("H", it.getString(1)) }
        migrated.query("SELECT pointsAwarded, creditedDate FROM completions WHERE id='c'").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)); assertEquals("2026-09-04", it.getString(1)) }
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('notification_preferences','notification_deliveries')").use { var count = 0; while (it.moveToNext()) count++; assertEquals(2, count) }
    }
}
