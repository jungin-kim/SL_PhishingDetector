package com.example.phishingdetector.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UrlCacheEntry::class, PhishingDomain::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun urlCacheDao(): UrlCacheDao
    abstract fun phishingDomainDao(): PhishingDomainDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phishing_detector.db"
                ).build().also { INSTANCE = it }
            }
    }
}
