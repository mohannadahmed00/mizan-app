package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.repository.AccountRepository
import com.giraffe.mizanapp.domain.repository.CodeRequest

/** Asks the account service to send a one-time code. Thin pass-through, kept for symmetry with [ConfirmSignInCode]. */
class RequestSignInCode(private val accounts: AccountRepository) {
    suspend operator fun invoke(email: String): CodeRequest = accounts.requestCode(email)
}
