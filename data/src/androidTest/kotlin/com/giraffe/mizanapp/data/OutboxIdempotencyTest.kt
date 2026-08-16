package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.sync.Outbox
import com.giraffe.mizanapp.data.sync.OutboxEntry
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `Outbox.enqueue` must be idempotent by construction (research R6): the id is
 * derived from `(entityType, entityId, operation)`, so re-enqueueing the same
 * logical change replaces the payload rather than duplicating a row.
 */
class OutboxIdempotencyTest : DbTestBase() {

    private lateinit var outbox: Outbox

    @Before
    fun setUpOutbox() {
        outbox = Outbox(db, time)
    }

    private fun entry(payload: String, entityId: String = "2026-08-16") = OutboxEntry(
        entityType = OutboxEntry.EntityType.DAY_RECORD,
        entityId = entityId,
        operation = OutboxEntry.Operation.UPSERT,
        payload = payload,
    )

    @Test
    fun enqueueing_the_same_change_five_times_leaves_one_row_with_the_newest_payload() = runBlocking {
        repeat(5) { i -> outbox.enqueue(entry(payload = "payload-$i")) }

        val due = outbox.due(now = time.now(), limit = 10)

        assertEquals(1, due.size)
        assertEquals("payload-4", due.single().payload)
    }

    @Test
    fun due_returns_oldest_createdAt_first() = runBlocking {
        outbox.enqueue(entry(payload = "first", entityId = "2026-08-01"))
        time.advanceBy(Duration.ofMinutes(1))
        outbox.enqueue(entry(payload = "second", entityId = "2026-08-02"))
        time.advanceBy(Duration.ofMinutes(1))
        outbox.enqueue(entry(payload = "third", entityId = "2026-08-03"))

        val due = outbox.due(now = time.now(), limit = 10)

        assertEquals(listOf("first", "second", "third"), due.map { it.payload })
    }

    @Test
    fun accepted_is_the_only_method_that_removes_a_row() = runBlocking {
        outbox.enqueue(entry(payload = "a", entityId = "2026-08-01"))
        outbox.enqueue(entry(payload = "b", entityId = "2026-08-02"))

        val ids = outbox.due(now = time.now(), limit = 10).map { it.id }
        outbox.accepted(listOf(ids.first()))

        val remaining = outbox.due(now = time.now(), limit = 10)
        assertEquals(1, remaining.size)
        assertEquals("b", remaining.single().payload)
    }

    @Test
    fun deferred_increments_attempts_and_moves_nextAttemptAt_without_removing_anything() = runBlocking {
        outbox.enqueue(entry(payload = "a"))
        val original = outbox.due(now = time.now(), limit = 10).single()
        assertEquals(0, original.attempts)

        val future = time.now().plus(Duration.ofHours(1))
        outbox.deferred(listOf(original.id), future)

        val notYetDue = outbox.due(now = time.now(), limit = 10)
        assertTrue(notYetDue.isEmpty())

        val dueLater = outbox.due(now = future, limit = 10)
        assertEquals(1, dueLater.size)
        assertEquals(1, dueLater.single().attempts)
    }
}
