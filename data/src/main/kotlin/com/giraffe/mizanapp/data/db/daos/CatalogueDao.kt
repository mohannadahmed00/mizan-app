package com.giraffe.mizanapp.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.mizanapp.data.db.entities.CatalogueVersionEntity
import com.giraffe.mizanapp.data.db.entities.SectionEntity
import com.giraffe.mizanapp.data.db.entities.TaskDefinitionEntity
import com.giraffe.mizanapp.data.db.entities.TaskVersionEntity

@Dao
interface CatalogueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSections(rows: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskDefinitions(rows: List<TaskDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVersions(rows: List<CatalogueVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskVersions(rows: List<TaskVersionEntity>)

    @Query("SELECT COUNT(*) FROM catalogue_versions")
    suspend fun countVersions(): Int

    @Query("SELECT MAX(version) FROM catalogue_versions")
    suspend fun currentVersion(): Int?

    /**
     * The version in effect on [date]: the greatest whose effective-from is on
     * or before it. When every version starts later, the earliest version
     * applies open-ended backwards — a catalogue applies until superseded,
     * and the earliest one has nothing before it to defer to (FR-013b,
     * research.md R1). Null only when no version exists at all, which is a
     * genuine absence and never a guess.
     */
    @Query(
        "SELECT COALESCE(" +
            "(SELECT MAX(version) FROM catalogue_versions WHERE effectiveFrom <= :date), " +
            "(SELECT MIN(version) FROM catalogue_versions)" +
            ")"
    )
    suspend fun versionEffectiveOn(date: String): Int?

    @Query("SELECT * FROM sections ORDER BY displayOrder")
    suspend fun sections(): List<SectionEntity>

    @Query("SELECT * FROM task_definitions")
    suspend fun taskDefinitions(): List<TaskDefinitionEntity>

    @Query("SELECT * FROM catalogue_versions ORDER BY version")
    suspend fun versions(): List<CatalogueVersionEntity>

    @Query("SELECT * FROM task_versions WHERE catalogueVersion = :version")
    suspend fun taskVersionsFor(version: Int): List<TaskVersionEntity>

    @Query("SELECT * FROM task_versions")
    suspend fun allTaskVersions(): List<TaskVersionEntity>

    @Query("SELECT COUNT(*) FROM task_definitions")
    suspend fun countTasks(): Int
}
