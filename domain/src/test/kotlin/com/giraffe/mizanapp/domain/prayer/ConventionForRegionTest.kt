package com.giraffe.mizanapp.domain.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

class ConventionForRegionTest {
    private val default = SelectedConvention(CalculationConvention.MUSLIM_WORLD_LEAGUE, AsrMadhab.STANDARD)
    private val mapping = RegionConventionMapping(1, default, listOf(
        RegionConventionEntry(listOf("Africa/Cairo"), CalculationConvention.EGYPTIAN, AsrMadhab.STANDARD),
        RegionConventionEntry(listOf("Asia/Riyadh"), CalculationConvention.UMM_AL_QURA, AsrMadhab.STANDARD),
    ))

    @Test fun cairoSelectsTheEgyptianConvention() = assertEquals(CalculationConvention.EGYPTIAN, conventionFor("Africa/Cairo", mapping).convention)
    @Test fun riyadhSelectsUmmAlQura() = assertEquals(CalculationConvention.UMM_AL_QURA, conventionFor("Asia/Riyadh", mapping).convention)
    @Test fun anUnmappedZoneSelectsTheDocumentedDefault() = assertEquals(default, conventionFor("America/New_York", mapping))
    @Test fun theDefaultIsMuslimWorldLeagueWithStandardAsr() = assertEquals(SelectedConvention(CalculationConvention.MUSLIM_WORLD_LEAGUE, AsrMadhab.STANDARD), mapping.default)
}
