package com.giraffe.mizanapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.giraffe.mizanapp.data.db.daos.CatalogueDao
import com.giraffe.mizanapp.data.db.daos.CompletionDao
import com.giraffe.mizanapp.data.db.daos.DayPlanDao
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.CompletionEntity
import com.giraffe.mizanapp.data.db.entities.DayPlanEntity
import com.giraffe.mizanapp.data.db.entities.PlannedTaskEntity
import com.giraffe.mizanapp.data.db.entities.SectionEntity
import com.giraffe.mizanapp.data.db.entities.TaskDefinitionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity

/**
 * The single source of truth for task recording and scoring (Principle IV).
 *
 * Schemas are exported to `data/schemas/` and committed. No destructive
 * migration may ever be added — a recorded day must survive every upgrade.
 */
@Database(
    entities = [
        SectionEntity::class,
        TaskDefinitionEntity::class,
        CatalogueVersionEntity::class,
        TaskVersionEntity::class,
        DayPlanEntity::class,
        PlannedTaskEntity::class,
        CompletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MizanDatabase : RoomDatabase() {

    abstract fun catalogueDao(): CatalogueDao

    abstract fun dayPlanDao(): DayPlanDao

    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "mizan.db"
    }
}
