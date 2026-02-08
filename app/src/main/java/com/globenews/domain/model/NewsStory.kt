package com.globenews.domain.model

import java.time.Instant

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
