package com.giraffe.mizanapp.domain.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveBoundaryDateTest {
    private val last = LocalDate.of(2026, 3, 13)

    @Test fun firstEverResolutionTakesTheComputedDate() =
        assertEquals(last.plusDays(2), resolveBoundaryDate(last.plusDays(2), null, false))
    @Test fun withinOneRegimeTheComputedDateIsAdoptedUnchanged() =
        assertEquals(last.plusDays(5), resolveBoundaryDate(last.plusDays(5), last, false))
    @Test fun withinOneRegimeAnEarlierComputedDateIsAdoptedUnchanged() =
        assertEquals(last.minusDays(1), resolveBoundaryDate(last.minusDays(1), last, false))
    @Test fun atASeamTheSameDateResolvesToItself() =
        assertEquals(last, resolveBoundaryDate(last, last, true))
    @Test fun atASeamAdvancingOneDayIsAllowed() =
        assertEquals(last.plusDays(1), resolveBoundaryDate(last.plusDays(1), last, true))
    @Test fun atASeamAdvancingTwoDaysIsClampedToOne() =
        assertEquals(last.plusDays(1), resolveBoundaryDate(last.plusDays(2), last, true))
    @Test fun atASeamGoingBackwardsIsClampedToTheLastResolvedDate() =
        assertEquals(last, resolveBoundaryDate(last.minusDays(1), last, true))
}
