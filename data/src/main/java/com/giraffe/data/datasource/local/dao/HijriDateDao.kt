package com.giraffe.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.giraffe.data.datasource.local.entity.CompactDateEntity

@Dao
interface HijriDateDao {
    @Query("SELECT * FROM compact_dates WHERE gregorianDateKey = :key LIMIT 1")
    suspend fun getByGregorianDate(key: String): CompactDateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dates: List<CompactDateEntity>)
}