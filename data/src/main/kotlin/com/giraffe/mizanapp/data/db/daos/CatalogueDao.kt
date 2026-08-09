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
     * or before it. Null when the date precedes every version — never a guess.
     */
    @Query(
        "SELECT MAX(version) FROM catalogue_versions WHERE effectiveFrom <= :date"
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
