package com.giraffe.mizanapp.data.sync

import androidx.room.withTransaction
import com.giraffe.mizanapp.data.db.MizanDatabase

/**
 * The only destructive operation on local storage in this product.
 *
 * Its two callers are `SupabaseAccountRepository.signOut(REMOVE_LOCAL_RECORDS)`
 * and the `replaceLocalRecords = true` branch of `confirmCode` — both only
 * after the caller has already obtained the user's confirmation naming what
 * is about to go (FR-007b, FR-013a). Clears exactly the record tables plus
 * sync bookkeeping; `sections`, `task_definitions`, `catalogue_versions` and
 * `task_versions` are untouched, so the app is usable the instant this
 * returns. Issues no remote call of any kind (FR-007d).
 */
class LocalRecordWipe(private val database: MizanDatabase) {

    suspend fun wipe() = database.withTransaction {
        database.completionDao().clear()
        database.dayPlanDao().clearPlannedTasks()
        database.dayPlanDao().clear()
        database.outboxDao().clear()
        database.syncCursorDao().clear()
        database.accountScopeDao().clear()
        database.leaderboardCacheDao().deleteAll()
        database.participationStateDao().deleteAll()
    }
}
