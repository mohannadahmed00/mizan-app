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
 * operation is stronger than one that merely avoids calling it.
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

    @Transaction
    suspend fun insertPlanWithTasks(plan: DayPlanEntity, tasks: List<PlannedTaskEntity>) {
        insertPlan(plan)
        insertPlannedTasks(tasks)
    }
}
