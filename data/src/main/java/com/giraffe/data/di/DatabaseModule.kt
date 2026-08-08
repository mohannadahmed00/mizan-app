package com.giraffe.data.di

import android.content.Context
import androidx.room.Room
import com.giraffe.data.datasource.local.dao.HijriDateDao
import com.giraffe.data.datasource.local.dao.TaskCompletionDao
import com.giraffe.data.datasource.local.dao.TaskDao
import com.giraffe.data.datasource.local.database.AppDatabase
import com.giraffe.data.datasource.local.seed.TaskSeeder
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@Configuration
class DatabaseModule {

    @Single
    fun provideDatabase(context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "compact-dates"
    ).fallbackToDestructiveMigration().addCallback(TaskSeeder()).build()

    @Single
    fun provideHijriDateDao(database: AppDatabase) = database.hijriDateDao()

    @Single
    fun provideTaskDao(database: AppDatabase) = database.taskDao()

    @Single
    fun provideTaskCompletionDao(database: AppDatabase) = database.taskCompletionDao()
}
