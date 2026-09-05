package com.giraffe.mizanapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.giraffe.mizanapp.data.db.dao.LeaderboardCacheDao
import com.giraffe.mizanapp.data.db.dao.ParticipationStateDao
import com.giraffe.mizanapp.data.db.daos.AccountScopeDao
import com.giraffe.mizanapp.data.db.daos.BoundaryStateDao
import com.giraffe.mizanapp.data.db.daos.CatalogueDao
import com.giraffe.mizanapp.data.db.daos.CompletionDao
import com.giraffe.mizanapp.data.db.daos.DayPlanDao
import com.giraffe.mizanapp.data.db.daos.OutboxDao
import com.giraffe.mizanapp.data.db.daos.SyncCursorDao
import com.giraffe.mizanapp.data.db.daos.NotificationDao
import com.giraffe.mizanapp.data.db.entity.LeaderboardCacheEntity
import com.giraffe.mizanapp.data.db.entity.ParticipationStateEntity
import com.giraffe.mizanapp.data.db.entities.AccountScopeEntity
import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.OutboxEntity
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import com.giraffe.mizanapp.data.db.entities.SectionEntity
import com.giraffe.mizanapp.data.db.entities.SyncCursorEntity
import com.giraffe.mizanapp.data.db.entities.TaskDefinitionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity
import com.giraffe.mizanapp.data.db.entities.NotificationPreferencesEntity
import com.giraffe.mizanapp.data.db.entities.NotificationDeliveryEntity

/**
 * The single source of truth for task recording and scoring (Principle IV).
 *
 * Schemas are exported to `data/schemas/` and committed. No destructive
 * migration may ever be added — a recorded day must survive every upgrade.
 */
@Database(
    entities = [
        SectionEntity::class,
        TaskDefinitionEntity::class,
        CatalogueVersionEntity::class,
        TaskVersionEntity::class,
        DayPlanEntity::class,
        PlannedTaskEntity::class,
        CompletionEntity::class,
        OutboxEntity::class,
        SyncCursorEntity::class,
        AccountScopeEntity::class,
        LeaderboardCacheEntity::class,
        ParticipationStateEntity::class,
        BoundaryStateEntity::class,
        NotificationPreferencesEntity::class,
        NotificationDeliveryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MizanDatabase : RoomDatabase() {

    abstract fun catalogueDao(): CatalogueDao

    abstract fun dayPlanDao(): DayPlanDao

    abstract fun completionDao(): CompletionDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncCursorDao(): SyncCursorDao

    abstract fun accountScopeDao(): AccountScopeDao

    abstract fun leaderboardCacheDao(): LeaderboardCacheDao

    abstract fun participationStateDao(): ParticipationStateDao
    abstract fun boundaryStateDao(): BoundaryStateDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val NAME = "mizan.db"
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notification_preferences (id INTEGER NOT NULL PRIMARY KEY, prayerWindowEnabled INTEGER NOT NULL DEFAULT 0, streakAtRiskEnabled INTEGER NOT NULL DEFAULT 0, weeklySummaryEnabled INTEGER NOT NULL DEFAULT 1, allSilenced INTEGER NOT NULL DEFAULT 0, quietStart TEXT, quietEnd TEXT, permissionAskedAt INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS notification_deliveries (anchorKey TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, state TEXT NOT NULL, reason TEXT, decidedAt INTEGER NOT NULL, heldUntil INTEGER)")
    }
}

/** Adds disposable leaderboard state without rewriting any recorded history. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS leaderboard_cache (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "periodKind TEXT NOT NULL, " +
                "periodStart TEXT NOT NULL, " +
                "regionId TEXT NOT NULL, " +
                "regionDisplayName TEXT NOT NULL, " +
                "payload TEXT NOT NULL, " +
                "retrievedAt INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS participation_state (" +
                "id INTEGER NOT NULL PRIMARY KEY, " +
                "optedIn INTEGER NOT NULL DEFAULT 0, " +
                "regionId TEXT, " +
                "regionDisplayName TEXT, " +
                "updatedAt INTEGER NOT NULL)",
        )
    }
}
