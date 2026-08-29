package com.giraffe.mizanapp

import androidx.compose.runtime.saveable.SaverScope
import com.giraffe.mizanapp.domain.policy.DayWritePolicy
import com.giraffe.mizanapp.today.FakeClock
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `destinationForDate` and the back stack (`005` FR-015, FR-015a, FR-015b) as
 * plain functions — no Activity, no Compose, no device required.
 */
class NavigationRoutingTest {

    private val clock = FakeClock()

    @Test
    fun `today routes to the Today destination from the week sheet`() {
        val destination = destinationForDate(clock.today(), clock.today())
        assertEquals(Destination.Today, destination)
    }

    @Test
    fun `today routes to the Today destination from history`() {
        // The routing function is identical regardless of caller - that IS
        // the guarantee (FR-023): one place decides, every caller obeys it.
        val destination = destinationForDate(clock.today(), clock.today())
        assertEquals(Destination.Today, destination)
    }

    @Test
    fun `an elapsed date routes to the DaySummary destination`() {
        val pastDate = clock.today().minusDays(1)
        val destination = destinationForDate(pastDate, clock.today())
        assertEquals(Destination.DaySummary(pastDate), destination)
    }

    @Test
    fun `back from a day opened in history returns to history, not the week sheet`() {
        var stack = listOf<Destination>(Destination.Today, Destination.Week, Destination.History)
        val date = clock.today().minusDays(1)
        stack = stack + destinationForDate(date, clock.today())
        assertEquals(Destination.DaySummary(date), stack.last())

        stack = stack.dropLast(1) // back
        assertEquals(Destination.History, stack.last())
    }

    @Test
    fun `back from a day opened in the week sheet returns to the week sheet`() {
        var stack = listOf<Destination>(Destination.Today, Destination.Week)
        val date = clock.today().minusDays(1)
        stack = stack + destinationForDate(date, clock.today())

        stack = stack.dropLast(1)
        assertEquals(Destination.Week, stack.last())
    }

    /** FR-015b: a date that was current when opened stops accepting writes once midnight passes. */
    @Test
    fun `a date that was current when opened stops accepting writes once midnight passes`() {
        val date = clock.today()
        assertEquals(Destination.Today, destinationForDate(date, clock.today()))

        clock.setDate(date.plusDays(1)) // midnight passes

        val policy = DayWritePolicy(clock)
        assertFalse("the date must no longer be writable", policy.isWritable(date))
        assertEquals(
            "re-resolving the same date must now yield the read-only detail",
            Destination.DaySummary(date),
            destinationForDate(date, clock.today()),
        )
    }

    @Test
    fun `back stack at the root has nothing to pop`() {
        val stack = listOf<Destination>(Destination.Today)
        assertTrue("a single-entry stack has nothing further back", stack.size == 1)
    }

    @Test
    fun `SignIn round-trips through encode and decode`() {
        assertEquals("SIGNIN", encode(Destination.SignIn))
        assertEquals(Destination.SignIn, decode("SIGNIN"))
    }

    @Test
    fun `a stack containing SignIn survives the StackSaver save and restore cycle`() {
        val stack = listOf<Destination>(Destination.Today, Destination.SignIn)
        val scope = SaverScope { true }
        val saved = with(StackSaver) { scope.save(stack) }
        val restored = StackSaver.restore(requireNotNull(saved))
        assertEquals(stack, restored)
    }

    @Test
    fun `Profile round-trips through encode and decode`() {
        assertEquals("PROFILE", encode(Destination.Profile))
        assertEquals(Destination.Profile, decode("PROFILE"))
    }

    @Test
    fun `a stack containing Profile survives the StackSaver save and restore cycle`() {
        val stack = listOf<Destination>(Destination.Today, Destination.Profile)
        val scope = SaverScope { true }
        val saved = with(StackSaver) { scope.save(stack) }
        val restored = StackSaver.restore(requireNotNull(saved))
        assertEquals(stack, restored)
    }

    @Test
    fun `an unrecognised token still falls back to Today`() {
        assertEquals(Destination.Today, decode("SOMETHING-UNKNOWN"))
    }
}
