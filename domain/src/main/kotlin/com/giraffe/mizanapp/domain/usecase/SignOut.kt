package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.identity.SignOutMode
import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.SyncRepository
import kotlinx.coroutines.flow.first

/**
 * Ends the session, reporting the pending count the caller must already have
 * surfaced (FR-007c) — read before [AccountRepository.signOut] acts, since a
 * removing sign-out clears the outbox that count is drawn from.
 */
class SignOut(private val accounts: AccountRepository, private val sync: SyncRepository) {
    suspend operator fun invoke(mode: SignOutMode): SignOutOutcome {
        val pending = sync.observePendingCount().first()
        accounts.signOut(mode)
        return SignOutOutcome(pending)
    }
}

data class SignOutOutcome(val pendingCount: Int)
