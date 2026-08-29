package com.giraffe.mizanapp.domain.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Viewer identity is the sole permitted row distinction and never implies rank styling. */
class MarkViewerTest {

    @Test
    fun marks_the_matching_row_only() {
        val result = markViewer(entries(), "user-2")

        assertFalse(result[0].isViewer)
        assertTrue(result[1].isViewer)
        assertFalse(result[2].isViewer)
    }

    @Test
    fun marks_nothing_when_the_viewer_id_is_null() {
        val result = markViewer(entries(), null)

        assertTrue(result.none(RankingEntry::isViewer))
    }

    @Test
    fun leaves_the_last_entry_identical_when_it_is_not_the_viewer() {
        val input = entries()

        val result = markViewer(input, "user-1")

        assertEquals(input.last(), result.last())
        assertFalse(result.last().isViewer)
    }

    private fun entries(): List<RankingEntry> = listOf(
        RankingEntry("user-1", "One", 30, 1, false),
        RankingEntry("user-2", "Two", 20, 2, false),
        RankingEntry("user-3", "Three", 10, 3, false),
    )
}
