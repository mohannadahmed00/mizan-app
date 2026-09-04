package com.giraffe.mizanapp.domain.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryLedgerTest {
    private fun record(state: DeliveryState) = DeliveryRecord("key", NotificationCategory.WEEKLY_SUMMARY, state, null, Instant.EPOCH, null)
    @Test fun `only terminal records are returned`() { assertEquals(DeliveryState.DELIVERED, listOf(record(DeliveryState.DELIVERED)).terminalFor("key")?.state); assertEquals(DeliveryState.DISCARDED, listOf(record(DeliveryState.DISCARDED)).terminalFor("key")?.state); assertNull(listOf(record(DeliveryState.HELD)).terminalFor("key")); assertNull(emptyList<DeliveryRecord>().terminalFor("key")) }
}
