package com.example.phishingdetector.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// ========================================
// 피싱 도메인 DAO
// ========================================
@Dao
interface PhishingDomainDao {
    // phishing_domains 테이블의 domain 컬럼을 모두 가져와서 List<String>으로 리턴
    @Query("SELECT domain FROM phishing_domains")
    suspend fun getAllDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(domains: List<PhishingDomain>)

    @Query("DELETE FROM phishing_domains")
    suspend fun clearAll()
}
