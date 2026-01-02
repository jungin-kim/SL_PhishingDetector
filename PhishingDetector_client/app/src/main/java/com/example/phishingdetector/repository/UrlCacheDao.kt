package com.example.phishingdetector.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// ========================================
// URL 캐시 DAO
// ========================================
@Dao
interface UrlCacheDao {
    // url 컬럼이 PRIMARY KEY이므로 한 건만 리턴 → UrlCacheEntry?
    @Query("SELECT * FROM url_cache WHERE url = :url")
    suspend fun getEntry(url: String): UrlCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: UrlCacheEntry)

    @Query("DELETE FROM url_cache WHERE lastChecked < :expiryTime")
    suspend fun deleteExpired(expiryTime: Long)

    @Query("DELETE FROM url_cache")
    suspend fun clearAll()
}
