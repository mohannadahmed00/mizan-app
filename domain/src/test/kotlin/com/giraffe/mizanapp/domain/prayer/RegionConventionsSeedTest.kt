package com.giraffe.mizanapp.domain.prayer

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionConventionsSeedTest {
    @Test
    fun shippedSeedIsValid() {
        val mapping = loadRegionConventionMapping()

        assertTrue(mapping.version > 0)
        assertEquals(
            SelectedConvention(CalculationConvention.MUSLIM_WORLD_LEAGUE, AsrMadhab.STANDARD),
            mapping.default,
        )
        assertTrue(CalculationConvention.entries.contains(mapping.default.convention))
        assertTrue(AsrMadhab.entries.contains(mapping.default.asr))
        mapping.regions.forEach { entry ->
            assertTrue("every zoneIds array must be non-empty", entry.zoneIds.isNotEmpty())
            assertTrue(CalculationConvention.entries.contains(entry.convention))
            assertTrue(AsrMadhab.entries.contains(entry.asr))
            entry.zoneIds.forEach(ZoneId::of)
        }
        val zoneIds = mapping.regions.flatMap { it.zoneIds }
        assertEquals("no zone may choose two conventions", zoneIds.size, zoneIds.toSet().size)
    }
}
