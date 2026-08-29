package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.SupabaseAccountRepository
import com.giraffe.mizanapp.data.sync.AccountScope
import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.createSupabaseClient
import com.giraffe.mizanapp.domain.identity.AccountSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * FR-005/FR-006's session-restoration contract, exercised against a real
 * Supabase client so `observeSession()`'s composition of Supabase's own
 * session status with [AccountScope] is proven, not assumed. Skipped when the
 * build carries no Supabase configuration.
 *
 * A full "sign in, kill the process, come back signed in" round trip needs a
 * real one-time code from a real inbox and is exercised by hand in quickstart
 * SC-001 (T072) rather than scripted here. What *is* scripted, deterministically
 * and without a live session: Supabase's session status is authoritative for
 * whether the device is currently signed in — a stale or absent [AccountScope]
 * entry never fabricates a session, and a session-status check never mutates
 * [AccountScope] itself, which is exactly what lets FR-006 return `SignedOut`
 * with every local record still intact when renewal fails.
 */
class SessionPersistenceTest : DbTestBase() {

    @Test
    fun with_no_live_session_observeSession_reports_SignedOut_and_leaves_AccountScope_untouched() = runBlocking {
        val client = createSupabaseClient()
        assumeTrue("no Supabase configuration in this build", client != null)

        val scope = AccountScope(db.accountScopeDao(), time)
        val repository = SupabaseAccountRepository(client, scope, db, Outbox(db, time), time)

        val session = repository.observeSession().first()

        assertEquals(AccountSession.SignedOut, session)
        assertEquals(AccountSession.SignedOut, scope.current())
    }

    @Test
    fun a_stale_AccountScope_entry_with_no_live_session_still_reports_SignedOut() = runBlocking {
        val client = createSupabaseClient()
        assumeTrue("no Supabase configuration in this build", client != null)

        val scope = AccountScope(db.accountScopeDao(), time)
        scope.set(userId = "stale-user", email = "stale@example.test", displayName = null)
        val repository = SupabaseAccountRepository(client, scope, db, Outbox(db, time), time)

        // Supabase's own session status is authoritative: a device that once
        // carried an account's records but has no live token is signed out,
        // never silently re-authenticated from AccountScope alone.
        val session = repository.observeSession().first()

        assertEquals(AccountSession.SignedOut, session)
    }
}
