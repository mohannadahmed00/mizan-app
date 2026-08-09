package com.giraffe.mizanapp.domain.repository

import com.giraffe.mizanapp.domain.day.Completion
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The append-only record of what was completed.
 *
 * Every read counts only live rows: a reversed record consumes no occurrence
 * slot, contributes no points, and appears in no list. Nothing outside a future
 * sync path has any reason to see a tombstone.
 */
interface CompletionRepository {

    /**
     * Records one occurrence, awarding the points from the **planned task** —
     * not the live catalogue (FR-011).
     *
     * Consults `DayWritePolicy` first and returns [RecordOutcome.NotWritable]
     * without touching storage when the date is refused (FR-015).
     */
    suspend fun record(date: LocalDate, taskSlug: String): RecordOutcome

    /**
     * Reverses the most recent live record for that task on that date, writing
     * a tombstone rather than deleting (FR-014).
     *
     * Frees exactly one occurrence slot, however many reversed records already
     * exist (FR-013a).
     */
    suspend fun undoLast(date: LocalDate, taskSlug: String): UndoOutcome

    fun observeCompletions(date: LocalDate): Flow<List<Completion>>

    suspend fun liveCount(date: LocalDate, taskSlug: String): Int
}

sealed interface RecordOutcome {
    data class Recorded(val completion: Completion, val liveCount: Int) : RecordOutcome

    /** At the limit is an ordinary state, not an error (Principle IX). */
    data class AtLimit(val limit: Int) : RecordOutcome
    data class NotWritable(val reason: String) : RecordOutcome
}

sealed interface UndoOutcome {
    data class Reversed(val completion: Completion, val liveCount: Int) : UndoOutcome

    /** Silent and harmless — never an error state. */
    data object NothingToUndo : UndoOutcome
    data class NotWritable(val reason: String) : UndoOutcome
}
