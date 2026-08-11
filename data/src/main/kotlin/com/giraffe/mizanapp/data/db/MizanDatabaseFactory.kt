package com.giraffe.mizanapp.data.db

import android.content.Context
import androidx.room.Room

/**
 * Builds the database.
 *
 * Lives here rather than in `:app`'s DI wiring so that Room stays behind the
 * `:data` boundary — `:app` should not need to import a persistence framework
 * to assemble the graph.
 *
 * **No destructive migration, ever** (constitution, Technology Constraints). A
 * recorded day must survive every upgrade; losing history to a schema change is
 * the one failure that cannot be repaired.
 */
fun createMizanDatabase(context: Context): MizanDatabase =
    Room.databaseBuilder(context, MizanDatabase::class.java, MizanDatabase.NAME)
        .addMigrations(MIGRATION_1_2)
        .build()
