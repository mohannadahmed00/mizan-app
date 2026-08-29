package com.giraffe.mizanapp.sync

import com.giraffe.mizanapp.domain.sync.SyncStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every [SyncStatus] renders exactly the copy `contracts/ui-state.md` names,
 * and none of it uses a word Principle IX and Conventions §6 forbid
 * (SC-011).
 *
 * T132 audit — every user-visible string this increment added, checked
 * against Conventions §6's forbidden list (`failed`, `failure`, `error`,
 * `lost`, `missing`, `problem`, `wrong`, `you didn't`, `you haven't`,
 * `retry now`) and against no red/orange/amber colour value:
 *
 * - `SyncStatusBar` (`syncStatusText`): "Backed up", "n changes waiting to be
 *   sent", "Not syncing right now", "Still loading earlier days";
 *   `NotSignedIn` renders nothing at all. Clean — asserted by this file.
 * - Sign-in steps (`SignInScreen.kt`): "Sign in with your email", "That
 *   address wasn't accepted.", "Sending a code to …", "A code was sent to
 *   …", "Resend code" / "Resend available shortly", "Checking the code…",
 *   the `CodeRejection.EXPIRED` / `INCORRECT` messages ("That code has
 *   expired. Request a new one." / "That code didn't match. Try entering it
 *   again."), "Signing in needs a connection. The rest of the app still
 *   works offline.", and the account-switch confirmation ("This device
 *   currently holds …'s records." / "… will be removed from this device
 *   only — …'s account keeps everything."). Clean — asserted by
 *   `SignInScreenTest.no_forbidden_word_appears_in_any_node_text`.
 * - Both sign-out confirmations and the profile screen (`ProfileScreen.kt`):
 *   "Sign out of this device?", "This removes N recorded days and N
 *   completions from this device. Your account keeps everything.", "N
 *   changes are still waiting to be sent.", "Sign out and remove data from
 *   this device", the conflict-policy line (below). Clean — asserted by
 *   `ProfileViewModelTest.no_state_string_contains_a_forbidden_word`.
 * - The conflict-policy line (FR-019a, [ProfileUiState.conflictPolicy]):
 *   "If you record on two devices at once, both records are kept. If you
 *   undo something on one device, it stays undone on the other." Clean —
 *   deliberately framed around what is kept, never what was missed.
 * - Still-loading labels: `DayCellState.NOT_YET_KNOWN` → "Still loading"
 *   (`HistoryScreen.kt`); the three provisional notices in
 *   `InsightsScreen.kt` — "Still loading — may change once more history
 *   arrives", "Still loading — percentages may change once more history
 *   arrives", "Still loading — some days below aren't fetched yet". Clean —
 *   framed as still-in-progress, never as empty or absent (FR-023b/d).
 * - No `Color` literal added anywhere in this increment falls in the
 *   red/orange/amber range — asserted by `DayCellColorsTest` and
 *   `SyncStatusBarTest`.
 */
class SyncStatusCopyTest {

    private val forbidden = listOf(
        "failed", "failure", "error", "lost", "missing", "problem", "wrong",
        "you didn't", "you haven't", "retry now",
    )

    @Test
    fun `NotSignedIn renders nothing`() {
        assertNull(syncStatusText(SyncStatus.NotSignedIn))
    }

    @Test
    fun `UpToDate renders Backed up`() {
        assertEquals("Backed up", syncStatusText(SyncStatus.UpToDate))
    }

    @Test
    fun `Pending renders the count`() {
        assertEquals("7 changes waiting to be sent", syncStatusText(SyncStatus.Pending(7)))
    }

    @Test
    fun `NotSyncing renders Not syncing right now`() {
        assertEquals("Not syncing right now", syncStatusText(SyncStatus.NotSyncing))
    }

    @Test
    fun `LoadingEarlierDays renders Still loading earlier days`() {
        assertEquals(
            "Still loading earlier days",
            syncStatusText(SyncStatus.LoadingEarlierDays(LocalDate.of(2026, 6, 1))),
        )
    }

    @Test
    fun `no rendered copy contains a forbidden word`() {
        val statuses = listOf(
            SyncStatus.NotSignedIn,
            SyncStatus.UpToDate,
            SyncStatus.Pending(3),
            SyncStatus.NotSyncing,
            SyncStatus.LoadingEarlierDays(null),
        )
        for (status in statuses) {
            val text = syncStatusText(status) ?: continue
            for (word in forbidden) {
                assertFalse("\"$text\" must not contain \"$word\"", text.contains(word, ignoreCase = true))
            }
        }
    }
}
