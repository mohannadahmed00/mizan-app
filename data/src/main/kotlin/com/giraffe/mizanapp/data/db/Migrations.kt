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
