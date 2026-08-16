package com.giraffe.mizanapp.domain.usecase

import com.giraffe.mizanapp.domain.sync.RecordCoverage
import com.giraffe.mizanapp.domain.time.FakeTimeProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streak is derived from the record on every emission — while this
 * device's coverage over its range is incomplete, the streak it reports is
 * marked provisional rather than presented as final (FR-023d).
 */
class GetStreakSummaryCoverageTest {

    private val time = FakeTimeProvider().apply { setDate(LocalDate.parse("2026-08-16")) }

    @Test
    fun `provisional while coverage over the streak's range is incomplete`() = runTest {
        val coverage = FakeRecordCoverageRepository(
            RecordCoverage(knownFrom = LocalDate.parse("2026-08-01"), complete = false),
        )
        val useCase = GetStreakSummary(FakeWeekCompletionRepository(), FakeWeekDayPlanRepository(time = time), time, coverage)

        val summary = useCase().first()

        assertTrue("streak must be provisional while coverage is incomplete", summary.provisional)
    }

    @Test
    fun `not provisional once coverage is complete`() = runTest {
        val coverage = FakeRecordCoverageRepository(RecordCoverage.completeFrom(LocalDate.parse("2026-01-01")))
        val useCase = GetStreakSummary(FakeWeekCompletionRepository(), FakeWeekDayPlanRepository(time = time), time, coverage)

        val summary = useCase().first()

        assertFalse(summary.provisional)
    }
}
