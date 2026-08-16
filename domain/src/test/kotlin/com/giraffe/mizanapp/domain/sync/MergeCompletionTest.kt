package com.giraffe.mizanapp.domain.sync

import com.giraffe.mizanapp.domain.day.Completion
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeCompletionTest {

    private val recordedAt: Instant = Instant.parse("2026-08-16T05:30:00Z")
    private val reversedAt: Instant = Instant.parse("2026-08-16T06:00:00Z")

    private fun completion(reversed: Instant? = null) = Completion(
        id = "6f2c0f6e-0000-4000-8000-000000000001",
        dayPlanId = "plan-local",
        taskSlug = "fajr-1",
        creditedDate = LocalDate.of(2026, 8, 16),
        pointsAwarded = 2,
        recordedAt = recordedAt,
        reversedAt = reversed,
    )

    @Test
    fun `a completion only one side holds survives untouched`() {
        assertEquals(completion(), mergeCompletion(local = completion(), remote = null))
        assertEquals(completion(), mergeCompletion(local = null, remote = completion()))
    }

    @Test
    fun `a tombstone on either side wins`() {
        assertFalse(mergeCompletion(completion(reversedAt), completion()).isLive)
        assertFalse(mergeCompletion(completion(), completion(reversedAt)).isLive)
    }

    /**
     * The property that makes this safe without a clock: a device that still
     * believes the record is live must never resurrect one the other device
     * undid (FR-018).
     */
    @Test
    fun `a live copy never clears a tombstone`() {
        val merged = mergeCompletion(local = completion(reversedAt), remote = completion())

        assertNotNull(merged.reversedAt)
        assertEquals(reversedAt, merged.reversedAt)
    }

    @Test
    fun `the merge is commutative`() {
        assertEquals(
            mergeCompletion(completion(reversedAt), completion()),
            mergeCompletion(completion(), completion(reversedAt)),
        )
    }

    @Test
    fun `the merge is idempotent`() {
        val merged = mergeCompletion(completion(reversedAt), completion())

        assertEquals(merged, mergeCompletion(merged, completion()))
        assertEquals(merged, mergeCompletion(merged, merged))
    }

    @Test
    fun `the merge is associative across three devices`() {
        val a = completion()
        val b = completion(reversedAt)
        val c = completion()

        assertEquals(
            mergeCompletion(mergeCompletion(a, b), c),
            mergeCompletion(a, mergeCompletion(b, c)),
        )
    }

    /**
     * Principle III at the merge boundary. Two copies disagreeing about an
     * earned figure is not something sync gets to arbitrate — the figure was
     * frozen when the record was made.
     */
    @Test
    fun `the frozen fields are never recomputed or averaged`() {
        val merged = mergeCompletion(completion(), completion(reversedAt))

        assertEquals(2, merged.pointsAwarded)
        assertEquals(recordedAt, merged.recordedAt)
        assertEquals(LocalDate.of(2026, 8, 16), merged.creditedDate)
        assertEquals("fajr-1", merged.taskSlug)
    }

    @Test
    fun `two live copies stay live`() {
        assertTrue(mergeCompletion(completion(), completion()).isLive)
    }

    @Test
    fun `merging nothing with nothing is a programming error, not a silent default`() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeCompletion(local = null, remote = null)
        }
    }
}
