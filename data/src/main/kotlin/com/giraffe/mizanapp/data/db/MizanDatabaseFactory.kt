package com.giraffe.mizanapp.data.db

import android.content.Context
import androidx.room.Room
import com.giraffe.mizanapp.data.db.daos.AccountScopeDao
import com.giraffe.mizanapp.data.db.daos.BoundaryStateDao

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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()

/**
 * `:app`'s DI wiring needs [com.giraffe.mizanapp.data.sync.AccountScope]'s DAO
 * directly (every other repository takes the whole [MizanDatabase] instead),
 * and calling `database.accountScopeDao()` straight from `:app` fails to
 * compile there — `MizanDatabase`'s `RoomDatabase` supertype isn't on `:app`'s
 * classpath (`implementation`, not `api`). This keeps that resolution inside
 * `:data`, same as [createMizanDatabase].
 */
fun accountScopeDaoOf(database: MizanDatabase): AccountScopeDao = database.accountScopeDao()

/** Same resolution as [accountScopeDaoOf], for [com.giraffe.mizanapp.data.db.daos.BoundaryStateDao]. */
fun boundaryStateDaoOf(database: MizanDatabase): BoundaryStateDao = database.boundaryStateDao()
