package com.giraffe.data.datasource.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.giraffe.data.datasource.local.dao.HijriDateDao
import com.giraffe.data.datasource.local.dao.TaskCompletionDao
import com.giraffe.data.datasource.local.dao.TaskDao
import com.giraffe.data.datasource.local.entity.CompactDateEntity
import com.giraffe.data.datasource.local.entity.TaskCompletionEntity
import com.giraffe.data.datasource.local.entity.TaskEntity

@Database(
    entities = [
        CompactDateEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hijriDateDao(): HijriDateDao
    abstract fun taskDao(): TaskDao
    abstract fun taskCompletionDao(): TaskCompletionDao
}
