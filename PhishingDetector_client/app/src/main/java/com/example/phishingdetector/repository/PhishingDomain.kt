package com.example.phishingdetector.repository

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// ========================================
// 2) 피싱 도메인 목록용 테이블
// ========================================
@Entity(tableName = "phishing_domains")
data class PhishingDomain(
    @PrimaryKey val domain: String,
    val updatedAt: Long
)