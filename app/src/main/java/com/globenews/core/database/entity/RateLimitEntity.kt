package com.globenews.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rate_limits")
data class RateLimitEntity(
    @PrimaryKey val sourceId: String,
    val requestCount: Int,
    val maxRequests: Int,
    val windowStartMillis: Long,
    val windowDurationMillis: Long
)
