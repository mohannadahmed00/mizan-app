package com.giraffe.mizanapp.leaderboard

import com.giraffe.mizanapp.domain.leaderboard.PeriodKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * T085 audit — every user-visible string this increment (008) adds, checked
 * against Conventions §6's forbidden list (`failed`, `failure`, `error`,
 * `lost`, `missing`, `problem`, `wrong`, `you didn't`, `you haven't`,
 * `retry now`, `behind`, `beat`, `beaten`, `overtake`, `climb`, `drop`,
 * `fell`, `only`, `just`) and against the `CLAUDE.md` Principle IX list:
 *
 * - `periodLabel()` (`LeaderboardSection.kt`): "Today", "This week, Saturday
 *   to Friday", "This month". Clean — asserted by this file.
 * - The opt-in panel (`OptInPanel.kt`): "Join the leaderboard", the
 *   name/points/region disclosure, the leave-anytime disclosure, and the
 *   offline-sync disclosure. Clean — asserted by
 *   `OptInPanelTest.all_irreversible_visibility_terms_appear_before_joining_is_available`.
 * - The leave control (`OptInPanel.kt`'s `LeaveControl`): "Leave the
 *   leaderboard", "Leave", "Stay", and the confirmation stating the
 *   forward/backward asymmetry. No retention plea — asserted by
 *   `LeaveControlTest.confirming_states_the_forward_backward_asymmetry_with_no_retention_plea`.
 * - The unavailable and cached ranking states (`LeaderboardSection.kt`):
 *   "Standings aren't available right now", "As of HH:MM". Clean — asserted
 *   by `LeaderboardDegradationTest`.
 * - The Honor Board panel (`HonorBoardPanel.kt`): "Honor Board" and each
 *   member's own display name — nothing else, and nothing about a
 *   non-qualifying viewer. Clean — asserted by `HonorBoardPanelTest`.
 * - "Regional standings" (section heading) and the "N points" / position
 *   text in `RankingRows` and `OwnRankRow` — numbers and a static heading,
 *   no comparative language. Visually inspected, clean.
 *
 * T086 colour audit — `grep -n "Color(" app/src/main/java/com/giraffe/mizanapp/leaderboard`
 * and a search for `#`/`0xFF` hex literals across the same directory both
 * return nothing. Every colour reference in this increment's files
 * (`LeaderboardSection.kt`, `OptInPanel.kt`, `HonorBoardPanel.kt`) is a
 * `MaterialTheme.colorScheme` token — `.primary` (the viewer's own row,
 * `alpha = 0.08f`) or `.surface` (every other row) — never a raw value, so
 * none can fall in the red/orange/amber range. In `RankingRows`, the
 * container colour is keyed only on `entry.isViewer`, never on `position`:
 * the last-place row and the first-place row receive the identical
 * `.surface` container whenever neither is the viewer (FR-038).
 */
class LeaderboardCopyTest {

    private val forbidden = listOf(
        "failed", "failure", "error", "lost", "missing", "problem", "wrong",
        "you didn't", "you haven't", "retry now", "behind", "beat", "beaten",
        "overtake", "climb", "drop", "fell", "only", "just",
    )

    @Test
    fun `daily period label`() {
        assertEquals("Today", periodLabel(PeriodKind.DAILY))
    }

    @Test
    fun `weekly period label states its Saturday to Friday span`() {
        assertEquals("This week, Saturday to Friday", periodLabel(PeriodKind.WEEKLY))
    }

    @Test
    fun `monthly period label`() {
        assertEquals("This month", periodLabel(PeriodKind.MONTHLY))
    }

    @Test
    fun `no period label contains a forbidden word`() {
        PeriodKind.entries.forEach { kind ->
            val text = periodLabel(kind)
            forbidden.forEach { word ->
                assertFalse("\"$text\" must not contain \"$word\"", text.contains(word, ignoreCase = true))
            }
        }
    }
}
