package com.globenews.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_stories")
data class CachedStoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String?,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val countryCode: String?,
    val admin1: String?,
    val scope: String,
    val sourcesJson: String,
    val language: String,
    val sentiment: Float?,
    val categoriesJson: String,
    val isBookmarked: Boolean = false,
    val cachedAt: Long,
    val expiresAt: Long
)
