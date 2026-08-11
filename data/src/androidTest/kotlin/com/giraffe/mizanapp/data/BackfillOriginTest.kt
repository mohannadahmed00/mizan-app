package com.giraffe.mizanapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giraffe.mizanapp.domain.day.PlanOrigin
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A backfilled plan is marked distinctly from an opened one (FR-011), and it
 * is built from the schedule rule alone — the voluntary fast on a Monday
 * appears whether or not anyone was there to see it.
 */
@RunWith(AndroidJUnit4::class)
class BackfillOriginTest : DbTestBase() {

    @Test
    fun a_backfilled_plan_is_marked_backfilled_while_the_opened_one_stays_opened() = runTest {
        catalogue.seedIfNeeded()

        time.setDate(LocalDate.parse("2026-08-08")) // Saturday: opened
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-08"))

        time.setDate(LocalDate.parse("2026-08-13")) // jump ahead without visiting Monday
        dayPlans.ensurePlanFor(LocalDate.parse("2026-08-10")) // Monday: backfilled

        val backfilled = dayPlans.planFor(LocalDate.parse("2026-08-10"))
        val opened = dayPlans.planFor(LocalDate.parse("2026-08-08"))

        assertEquals(PlanOrigin.BACKFILLED, backfilled?.origin)
        assertEquals(PlanOrigin.OPENED, opened?.origin)
        assertEquals(
            "the fast is present because the schedule rule says so, not because anyone was there",
            74,
            backfilled?.availablePoints,
        )
    }
}
