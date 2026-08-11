package com.giraffe.mizanapp.domain.week

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeeklyScoreTest {

    @Test
    fun `fraction is zero when nothing has elapsed`() {
        val score = WeeklyScore(earned = 0, elapsedAvailable = 0, weekTarget = 500)

        assertEquals(0f, score.fraction, 0.0001f)
    }

    @Test
    fun `fraction divides by elapsed available never by week target`() {
        val score = WeeklyScore(earned = 120, elapsedAvailable = 281, weekTarget = 500)

        assertEquals(120f / 281f, score.fraction, 0.0001f)
    }

    @Test
    fun `negative earned is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyScore(earned = -1, elapsedAvailable = 100, weekTarget = 500)
        }
    }

    @Test
    fun `earned may not exceed elapsed available`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyScore(earned = 101, elapsedAvailable = 100, weekTarget = 500)
        }
    }

    @Test
    fun `elapsed available may not exceed the week target`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyScore(earned = 0, elapsedAvailable = 501, weekTarget = 500)
        }
    }
}
