package com.giraffe.mizanapp.domain.sync

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MergeDayRecordTest {

    private val date: LocalDate = LocalDate.of(2026, 8, 16)

    private fun record(version: Int) = DayRecord(date = date, catalogueVersion = version)

    @Test
    fun `a record only one side holds survives untouched`() {
        assertEquals(record(3), mergeDayRecord(local = record(3), remote = null))
        assertEquals(record(3), mergeDayRecord(local = null, remote = record(3)))
    }

    @Test
    fun `the older catalogue version wins`() {
        assertEquals(record(1), mergeDayRecord(local = record(1), remote = record(2)))
        assertEquals(record(1), mergeDayRecord(local = record(2), remote = record(1)))
    }

    /**
     * The safety property, stated as its own test because it is the reason `min`
     * was chosen over any timestamp rule: a newer publication must never be able
     * to claim a date already recorded under an older version (FR-024b).
     */
    @Test
    fun `a newer catalogue version can never win`() {
        val settled = mergeDayRecord(local = record(1), remote = record(99))

        assertEquals(1, settled.catalogueVersion)
    }

    @Test
    fun `the merge is commutative`() {
        assertEquals(
            mergeDayRecord(local = record(4), remote = record(7)),
            mergeDayRecord(local = record(7), remote = record(4)),
        )
    }

    @Test
    fun `the merge is idempotent`() {
        val settled = mergeDayRecord(local = record(2), remote = record(5))

        assertEquals(settled, mergeDayRecord(local = settled, remote = record(2)))
        assertEquals(settled, mergeDayRecord(local = settled, remote = record(5)))
        assertEquals(settled, mergeDayRecord(local = settled, remote = settled))
    }

    @Test
    fun `the merge is associative across three devices`() {
        val a = record(6)
        val b = record(3)
        val c = record(9)

        assertEquals(
            mergeDayRecord(mergeDayRecord(a, b), c),
            mergeDayRecord(a, mergeDayRecord(b, c)),
        )
    }

    @Test
    fun `the date is carried through, never merged`() {
        assertEquals(date, mergeDayRecord(local = record(1), remote = record(2)).date)
    }

    @Test
    fun `merging nothing with nothing is a programming error, not a silent default`() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeDayRecord(local = null, remote = null)
        }
    }
}
