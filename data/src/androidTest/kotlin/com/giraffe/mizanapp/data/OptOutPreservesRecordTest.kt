package com.giraffe.mizanapp.data

import com.giraffe.mizanapp.data.repository.RoomParticipationRepository
import com.giraffe.mizanapp.domain.day.scoreDay
import com.giraffe.mizanapp.domain.leaderboard.ParticipationResult
import com.giraffe.mizanapp.domain.repository.RecordOutcome
import com.giraffe.mizanapp.domain.streak.buildStreakSummary
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-003: opting out changes nothing about the participant's own record (FR-005). */
class OptOutPreservesRecordTest : DbTestBase() {

    @Test
    fun opting_out_leaves_every_days_score_and_the_streak_untouched_and_recording_still_works() = runTest {
        catalogue.seedIfNeeded()
        val dates = listOf("2026-03-10", "2026-03-11", "2026-03-12", "2026-03-13", "2026-03-14").map(LocalDate::parse)
        dates.forEach { date ->
            time.setDate(date)
            dayPlans.ensurePlanFor(date)
            completions.record(date, "fajr-1")
        }

        val recordStart = dayPlans.earliestPlanDate()
        val scoresBefore = dates.map { scoreFor(it) }
        val datesEngagedBefore = completions.observeConsistencyDates().first()
        val streakBefore = buildStreakSummary(datesEngagedBefore, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), recordStart)

        val fake = FakeRemoteDataSource()
        fake.currentUserId = "viewer"
        val participation = RoomParticipationRepository(db, fake)
        participation.optIn(ZoneId.of("Africa/Cairo"))
        assertEquals(ParticipationResult.Applied, participation.optOut())

        val scoresAfter = dates.map { scoreFor(it) }
        val datesEngagedAfter = completions.observeConsistencyDates().first()
        val streakAfter = buildStreakSummary(datesEngagedAfter, time.today(), time.now(), time.today().plusDays(1).atStartOfDay(time.zone()).toInstant(), recordStart)

        assertEquals(scoresBefore, scoresAfter)
        assertEquals(streakBefore, streakAfter)

        val outcome = completions.record(dates.last(), "adhkar")
        assertTrue("$outcome", outcome is RecordOutcome.Recorded)
    }

    private suspend fun scoreFor(date: LocalDate) =
        scoreDay(dayPlans.planFor(date)!!, completions.observeCompletions(date).first())
}
