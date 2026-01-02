package com.example.phishingdetector.repository

import android.content.Context

/**
 * PhishingDomain 도메인 사전 관리
 * 도메인 업데이트 시 Room에 덮어쓰기
 */
class DomainRepository private constructor(private val domainDao: PhishingDomainDao) {

    companion object {
        @Volatile
        private var INSTANCE: DomainRepository? = null

        fun getInstance(context: Context): DomainRepository {
            return INSTANCE ?: synchronized(this) {
                val dao = AppDatabase.getInstance(context).phishingDomainDao()
                val instance = DomainRepository(dao)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * 최신 도메인 목록으로 database 덮어쓰기
     */
    suspend fun refreshDomains(newList: List<String>) {
        domainDao.clearAll()
        val entities = newList.map { PhishingDomain(it, System.currentTimeMillis()) }
        domainDao.insertAll(entities)
    }

    /**
     * 도메인이 사전에 있으면 true (피싱 의심)
     */
    suspend fun isDomainPhishing(domain: String): Boolean {
        val all = domainDao.getAllDomains()
        return all.contains(domain)
    }
}
