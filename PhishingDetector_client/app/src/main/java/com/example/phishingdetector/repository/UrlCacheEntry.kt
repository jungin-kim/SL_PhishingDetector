package com.example.phishingdetector.repository

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// ========================================
// 1) URL 캐시용 테이블
// ========================================
@Entity(tableName = "url_cache")
data class UrlCacheEntry(
    @PrimaryKey @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "isPhishing")
    val isPhishing: Boolean,

    @ColumnInfo(name = "score")
    val score: Float,

    @ColumnInfo(name = "lastChecked")
    val lastChecked: Long
)
