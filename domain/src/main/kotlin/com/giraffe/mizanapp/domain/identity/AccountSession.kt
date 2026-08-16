package com.giraffe.mizanapp.domain.identity

/**
 * Whether this device currently holds a signed-in session, and for whom.
 *
 * [SignedOut] is not a degraded state. Every Phase 2–6 capability works in it,
 * on a fresh install, in airplane mode (FR-003), and a session that cannot be
 * renewed returns here with every local record intact (FR-006). Nothing in the
 * product may gate a capability on being [SignedIn].
 */
sealed interface AccountSession {

    data object SignedOut : AccountSession

    /**
     * [displayName] is optional and empty by default — never requested during
     * sign-in, never required to use anything, and shown as [email] wherever a
     * name would appear and none is set (FR-007e).
     */
    data class SignedIn(
        val userId: String,
        val email: String,
        val displayName: String? = null,
    ) : AccountSession
}
