package com.globenews.domain.repository

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.plugin.GeoBounds
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun getStoriesByRegion(
        bounds: GeoBounds,
        scopes: Set<EditorialScope>,
        forceRefresh: Boolean = false
    ): Flow<Result<List<NewsStory>>>

    fun getStoryById(id: String): Flow<NewsStory?>

    suspend fun bookmarkStory(storyId: String, bookmarked: Boolean)

    fun getBookmarkedStories(): Flow<List<NewsStory>>

    fun searchStories(query: String): Flow<List<NewsStory>>

    suspend fun clearCache()

    fun getCacheSizeBytes(): Flow<Long>
}
