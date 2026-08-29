package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.AccountRepository

/** Thin pass-through, kept for symmetry with [SignOut] (FR-007e). */
class UpdateDisplayName(private val accounts: AccountRepository) {
    suspend operator fun invoke(name: String?) {
        accounts.updateDisplayName(name)
    }
}
