package com.giraffe.mizanapp.domain.day

/**
 * How many times a task has been recorded on a day, and whether it may be
 * recorded again.
 *
 * **The `isLive` filter below is the most important line in this increment.**
 * Undo writes a tombstone rather than deleting, so a count over all rows would
 * lock a nine-occurrence task at nine after one mistaken tap — permanently, with
 * no way back and no visible reason. Every read path applies this filter.
 */
fun liveCount(completions: List<Completion>, taskSlug: String): Int =
    completions.count { it.taskSlug == taskSlug && it.isLive }

fun canRecord(completions: List<Completion>, task: PlannedTask): Boolean =
    liveCount(completions, task.taskSlug) < task.maxOccurrencesPerDay

/** The most recent live record for a task, or null when there is nothing to undo. */
fun latestLive(completions: List<Completion>, taskSlug: String): Completion? =
    completions.filter { it.taskSlug == taskSlug && it.isLive }.maxByOrNull { it.recordedAt }
