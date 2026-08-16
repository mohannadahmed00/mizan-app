package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanWithTasks
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Day plans are written once and never changed (Principle III).
 *
 * **There is deliberately no update method here** — not for points, not for the
 * Hijri label, not for anything. An interface that cannot express the forbidden
 * operation is stronger than one that merely avoids calling it. Sync adds three
 * more methods on top of that rule, not exceptions to it: the only columns any
 * method in this interface can write are `userId`, `updatedAt` and `syncedAt`.
 * A recorded day's date, catalogue version, and available-points total are
 * unreachable from here, which is how FR-024a is enforced structurally rather
 * than by discipline.
 */
@Dao
interface DayPlanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: DayPlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlannedTasks(tasks: List<PlannedTaskEntity>)

    @Transaction
    @Query("SELECT * FROM day_plans WHERE date = :date AND deletedAt IS NULL")
    suspend fun planByDate(date: String): DayPlanWithTasks?

    @Transaction
    @Query("SELECT * FROM day_plans WHERE date = :date AND deletedAt IS NULL")
    fun observePlanByDate(date: String): Flow<DayPlanWithTasks?>

    @Query("SELECT COUNT(*) FROM day_plans")
    suspend fun countPlans(): Int

    /** The week's stored plans, in date order. Never fabricates a missing date. */
    @Transaction
    @Query("SELECT * FROM day_plans WHERE date BETWEEN :start AND :end AND deletedAt IS NULL ORDER BY date")
    suspend fun plansBetween(start: String, end: String): List<DayPlanWithTasks>

    /** The record start — the earliest date with a plan, or null before any exists. */
    @Query("SELECT MIN(date) FROM day_plans WHERE deletedAt IS NULL")
    suspend fun earliestPlanDate(): String?

    @Transaction
    suspend fun insertPlanWithTasks(plan: DayPlanEntity, tasks: List<PlannedTaskEntity>) {
        insertPlan(plan)
        insertPlannedTasks(tasks)
    }

    /** Attributes every unclaimed local plan to [userId] on sign-in. Idempotent (FR-013). */
    @Query("UPDATE day_plans SET userId = :userId, updatedAt = :at WHERE userId IS NULL")
    suspend fun claimPlansForUser(userId: String, at: Long): Int

    /** Attributes every unclaimed local planned task to [userId] on sign-in. Idempotent (FR-013). */
    @Query("UPDATE planned_tasks SET userId = :userId, updatedAt = :at WHERE userId IS NULL")
    suspend fun claimPlannedTasksForUser(userId: String, at: Long): Int

    @Query("UPDATE day_plans SET syncedAt = :at WHERE date IN (:dates)")
    suspend fun markSynced(dates: List<String>, at: Long)

    @Query("SELECT * FROM day_plans WHERE syncedAt IS NULL")
    suspend fun unsynced(): List<DayPlanEntity>

    @Query("DELETE FROM day_plans")
    suspend fun clear()

    @Query("DELETE FROM planned_tasks")
    suspend fun clearPlannedTasks()
}
