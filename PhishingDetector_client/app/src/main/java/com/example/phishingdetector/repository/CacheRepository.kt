package com.example.phishingdetector.repository

import android.content.Context

/**
 * UrlCacheEntry 캐시 관리
 * TTL: 24시간 (예: 24 * 60 * 60 * 1000L)
 */
class CacheRepository private constructor(private val urlCacheDao: UrlCacheDao) {

    companion object {
        private var INSTANCE: CacheRepository? = null
        private const val TTL_MILLIS = 24 * 60 * 60 * 1000L  // 24시간

        fun getInstance(context: Context): CacheRepository {
            return INSTANCE ?: synchronized(this) {
                val dao = AppDatabase.getInstance(context).urlCacheDao()
                val instance = CacheRepository(dao)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * URL에 대한 유효한 캐시가 있는지 확인
     * @return UrlCacheEntry (만료되지 않은 경우) or null
     */
    suspend fun getValidCache(url: String): UrlCacheEntry? {
        val entry = urlCacheDao.getEntry(url)
        entry?.let {
            if (System.currentTimeMillis() - it.lastChecked <= TTL_MILLIS) {
                return it
            }
        }
        return null
    }

    /**
     * 캐시에 결과 저장 (또는 덮어쓰기)
     */
    suspend fun saveCache(url: String, isPhishing: Boolean, score: Float) {
        val entry = UrlCacheEntry(
            url = url,
            isPhishing = isPhishing,
            score = score,
            lastChecked = System.currentTimeMillis()
        )
        urlCacheDao.insertOrUpdate(entry)
    }

    /**
     * 만료된 캐시 삭제 (주기 작업에서 호출)
     */
    suspend fun deleteExpiredCache() {
        val expiryTime = System.currentTimeMillis() - TTL_MILLIS
        urlCacheDao.deleteExpired(expiryTime)
    }
}
