package com.giraffe.mizanapp.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.giraffe.mizanapp.domain.week.DayCellState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Every [DayCellState] maps to a colour, `NOT_YET_KNOWN`'s reads distinct from
 * both `NOTHING_RECORDED` and `OUTSIDE_RECORD` (FR-023b — conflating them is
 * exactly what it forbids), and no colour in this file carries a red, orange,
 * or amber hue (Principle IX). Calls the real [containerColorFor] — its
 * explicit-[androidx.compose.material3.ColorScheme] overload needs no
 * composition, so this runs as a plain JVM test.
 */
class DayCellColorsTest {

    private val schemes = listOf(lightColorScheme(), darkColorScheme())

    private fun isReddish(color: Color): Boolean =
        color.red > 0.5f && color.red > color.green + 0.15f && color.red > color.blue + 0.15f

    @Test
    fun `every DayCellState maps to a colour`() {
        for (scheme in schemes) {
            for (state in DayCellState.entries) {
                containerColorFor(state, scheme) // must not throw
            }
        }
    }

    @Test
    fun `NOT_YET_KNOWN differs from both NOTHING_RECORDED and OUTSIDE_RECORD`() {
        for (scheme in schemes) {
            val notYetKnown = containerColorFor(DayCellState.NOT_YET_KNOWN, scheme)
            val nothingRecorded = containerColorFor(DayCellState.NOTHING_RECORDED, scheme)
            val outsideRecord = containerColorFor(DayCellState.OUTSIDE_RECORD, scheme)

            assertNotEquals(notYetKnown, nothingRecorded)
            assertNotEquals(notYetKnown, outsideRecord)
        }
    }

    @Test
    fun `no colour has a red, orange, or amber hue`() {
        for (scheme in schemes) {
            for (state in DayCellState.entries) {
                val color = containerColorFor(state, scheme)
                assertFalse("$state must not be reddish: $color", isReddish(color))
            }
        }
    }
}
