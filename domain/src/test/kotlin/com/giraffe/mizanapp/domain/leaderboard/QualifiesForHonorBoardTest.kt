package com.giraffe.mizanapp.domain.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-011: qualification depends only on daysEngaged and threshold — never on points. */
class QualifiesForHonorBoardTest {

    @Test
    fun meeting_or_exceeding_the_threshold_qualifies() {
        assertTrue(qualifiesForHonorBoard(daysEngaged = 5, threshold = 5))
        assertTrue(qualifiesForHonorBoard(daysEngaged = 6, threshold = 5))
    }

    @Test
    fun falling_short_of_the_threshold_does_not_qualify() {
        assertFalse(qualifiesForHonorBoard(daysEngaged = 4, threshold = 5))
    }

    @Test
    fun two_participants_with_equal_days_but_very_different_points_qualify_identically() {
        // Points is not even a parameter here — the two participants' totals could be
        // 4 and 4000 and this function could not tell the difference (FR-027).
        val lowPointsQualifies = qualifiesForHonorBoard(daysEngaged = 5, threshold = 5)
        val highPointsQualifies = qualifiesForHonorBoard(daysEngaged = 5, threshold = 5)
        assertEquals(lowPointsQualifies, highPointsQualifies)
    }
}
