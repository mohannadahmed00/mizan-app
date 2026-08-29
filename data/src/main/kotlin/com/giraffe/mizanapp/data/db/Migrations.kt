package com.giraffe.mizanapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `day_plans.origin`, and nothing else.
 *
 * Purely additive — no row is rewritten, no figure moves, nothing is
 * dropped. Non-destructive by construction, which is the `develop-v1` ->
 * `main` release gate.
 *
 * The `'OPENED'` default is a fact, not a guess: `002`'s only creation path
 * is `ensurePlanFor` called for the current date at launch or rollover, so
 * no plan in a v1 database can be a backfill (FR-013e).
 *
 * **This migration must remain purely additive** — never drop, rename, or
 * rewrite a column here or in any migration that follows it.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE day_plans ADD COLUMN origin TEXT NOT NULL DEFAULT 'OPENED'")
    }
}

/**
 * Adds the sync tables and two nullable `syncedAt` columns, and nothing else.
 *
 * **Purely additive** — no DROP, no RENAME, no UPDATE, no row is rewritten.
 * `outbox`, `sync_cursors` and `account_scope` start empty; `syncedAt` starts
 * null on every existing row, which is correct (nothing has been synced yet)
 * and requires no backfill.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS outbox (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "entityType TEXT NOT NULL, " +
                "entityId TEXT NOT NULL, " +
                "operation TEXT NOT NULL, " +
                "payload TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL, " +
                "attempts INTEGER NOT NULL, " +
                "nextAttemptAt INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_outbox_nextAttemptAt ON outbox (nextAttemptAt)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_cursors (" +
                "`key` TEXT NOT NULL PRIMARY KEY, " +
                "value TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS account_scope (" +
                "id INTEGER NOT NULL PRIMARY KEY, " +
                "userId TEXT, " +
                "email TEXT, " +
                "displayName TEXT, " +
                "updatedAt INTEGER NOT NULL)",
        )
        db.execSQL("ALTER TABLE day_plans ADD COLUMN syncedAt INTEGER")
        db.execSQL("ALTER TABLE completions ADD COLUMN syncedAt INTEGER")
    }
}
