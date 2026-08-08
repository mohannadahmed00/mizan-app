package com.giraffe.data.datasource.local.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

class TaskSeeder : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Executors.newSingleThreadScheduledExecutor().execute {
            val tasks = listOf(
                listOf("Fajr", "FAJR", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Dhuhr", "DHUHR", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Asr", "ASR", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Maghrib", "MAGHRIB", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Isha", "ISHA", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Quran Reading", "QURAN", "10", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Morning Adhkar", "ADKAR", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Evening Adhkar", "ADKAR", "5", "SA,SU,MO,TU,WE,TH,FR"),
                listOf("Monday Fast", "FAST", "20", "MO"),
                listOf("Thursday Fast", "FAST", "20", "TH"),
            )
            tasks.forEach { (name, category, points, days) ->
                db.execSQL(
                    """INSERT OR IGNORE INTO tasks(name, category, points, activeDays, isActive)
                       VALUES (?, ?, ?, ?, 1)""",
                    arrayOf(name, category, points, days)
                )
            }
        }
    }
}
