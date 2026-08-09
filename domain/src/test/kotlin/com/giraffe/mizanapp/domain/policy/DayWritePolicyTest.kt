package com.giraffe.mizanapp.domain.policy

import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the current date accepts writes (FR-015).
 *
 * This rule lives in exactly one place so that roadmap Phase 5 can widen it
 * without two screens forming different opinions about whether a day is
 * writable.
 */
class DayWritePolicyTest {

    private val time = FakeTimeProvider()
    private val policy = DayWritePolicy(time)

    @Test
    fun `today is writable`() {
        assertTrue(policy.isWritable(time.today()))
    }

    @Test
    fun `yesterday is not writable`() {
        assertFalse(policy.isWritable(time.today().minusDays(1)))
    }

    @Test
    fun `tomorrow is not writable`() {
        assertFalse(policy.isWritable(time.today().plusDays(1)))
    }

    @Test
    fun `what counts as today follows the clock`() {
        val before = time.today()
        time.setDate(LocalDate.of(2027, 1, 1))

        assertFalse("the old today must stop being writable", policy.isWritable(before))
        assertTrue(policy.isWritable(LocalDate.of(2027, 1, 1)))
    }
}
