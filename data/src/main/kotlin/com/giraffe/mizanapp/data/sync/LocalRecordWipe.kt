package com.giraffe.mizanapp.data.sync

import com.giraffe.mizanapp.data.db.MizanDatabase

/**
 * The only destructive operation on local storage in this product.
 *
 * Its two callers are `SupabaseAccountRepository.signOut(REMOVE_LOCAL_RECORDS)`
 * and the `replaceLocalRecords = true` branch of `confirmCode` — both only
 * after the caller has already obtained the user's confirmation naming what
 * is about to go (FR-007b, FR-013a).
 */
class LocalRecordWipe(private val database: MizanDatabase) {

    suspend fun wipe() {
        TODO("T124")
    }
}
