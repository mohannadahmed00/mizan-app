package com.giraffe.data.datasource.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.giraffe.data.datasource.local.dao.HijriDateDao
import com.giraffe.data.datasource.local.entity.CompactDateEntity

@Database(entities = [CompactDateEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hijriDateDao(): HijriDateDao
}