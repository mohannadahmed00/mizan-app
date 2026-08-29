package com.giraffe.mizanapp.domain.identity

import java.time.Instant

/**
 * Where the one-time-code round trip has got to.
 *
 * **Every member carries the entered email.** That is not incidental: FR-002a
 * requires that no state in this flow — including every failure — discards the
 * address the user typed, so a mistyped address is corrected by editing rather
 * than retyped from scratch. A step without an email could not satisfy it, so
 * none exists.
 *
 * There is no password anywhere in this type, and none anywhere in the product
 * (FR-002).
 */
sealed interface SignInStep {

    val email: String

    data class EnteringEmail(
        override val email: String,
        val invalid: Boolean = false,
    ) : SignInStep

    data class RequestingCode(override val email: String) : SignInStep

    /** [resendAvailableAt] is stated to the user rather than left to a silent no-op. */
    data class AwaitingCode(
        override val email: String,
        val resendAvailableAt: Instant,
    ) : SignInStep

    data class Confirming(
        override val email: String,
        val code: String,
    ) : SignInStep

    /** An ordinary outcome, not an error. Returns to [AwaitingCode] on the next edit. */
    data class CodeNotAccepted(
        override val email: String,
        val reason: CodeRejection,
    ) : SignInStep

    /**
     * A statement of fact, not a failure: signing in needs a connection, and the
     * whole app behind this screen stays usable without one (US1 AS3).
     */
    data class NeedsConnection(override val email: String) : SignInStep
}

enum class CodeRejection { EXPIRED, INCORRECT }
