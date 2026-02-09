package com.globenews.domain.model

import java.time.Instant

/**
 * Core domain model for a geolocated news story.
 *
 * Stories are aggregated from multiple providers (GDELT, GNews, NewsAPI, RSS)
 * and deduplicated by URL/title similarity. A single story may have multiple
 * [sources] if the same event was reported by different outlets.
 *
 * [scope] determines which zoom levels the story appears at (see
 * GetStoriesByRegionUseCase). [location] places the story on the map.
 */
data class NewsStory(
    val id: String,
    val title: String,
    val summary: String?,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Instant,
    val location: StoryLocation,
    val scope: EditorialScope,
    val sources: List<SourceAttribution>,
    val language: String,
    val sentiment: Float?,
    val categories: List<String>,
    val isBookmarked: Boolean = false
)
