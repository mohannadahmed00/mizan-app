package com.giraffe.data.di

import android.content.Context
import androidx.room.Room
import com.giraffe.data.datasource.local.database.AppDatabase
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
    ).build()

    @Single
    fun provideHijriDateDao(database: AppDatabase) = database.hijriDateDao()
}