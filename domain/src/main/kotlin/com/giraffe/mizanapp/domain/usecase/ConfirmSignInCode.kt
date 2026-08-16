package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeConfirmation
import com.giraffe.mizanapp.domain.repository.SyncRepository

/**
 * Completes sign-in and, only on success, kicks off the migration sync.
 *
 * `syncNow()` is called for exactly one outcome — [CodeConfirmation.SignedIn] —
 * never for an ordinary rejection, an offline attempt, or
 * [CodeConfirmation.WouldReplaceLocalRecords], which opens no session at all.
 */
class ConfirmSignInCode(private val accounts: AccountRepository, private val sync: SyncRepository) {
    suspend operator fun invoke(
        email: String,
        code: String,
        replaceLocalRecords: Boolean = false,
    ): CodeConfirmation {
        TODO("T061")
    }
}
