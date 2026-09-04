package com.giraffe.mizanapp.domain.week

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryDormancyTest {
 @Test fun `three most recent empty weeks are dormant`() { assertFalse(isSummaryDormant(emptyList())); assertFalse(isSummaryDormant(listOf(false, false))); assertTrue(isSummaryDormant(listOf(false, false, false))); assertFalse(isSummaryDormant(listOf(false, false, true, false))) }
}
