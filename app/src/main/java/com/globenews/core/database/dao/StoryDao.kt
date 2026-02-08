package com.globenews.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.globenews.core.database.entity.CachedStoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stories: List<CachedStoryEntity>)

    @Query(
        """SELECT * FROM cached_stories
        WHERE latitude BETWEEN :south AND :north
        AND longitude BETWEEN :west AND :east
        AND scope IN (:scopes)
        AND expiresAt > :now
        ORDER BY publishedAt DESC
        LIMIT :limit"""
    )
    fun getStoriesByRegion(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        scopes: List<String>,
        now: Long,
        limit: Int = 500
    ): Flow<List<CachedStoryEntity>>

    @Query("SELECT * FROM cached_stories WHERE id = :id")
    fun getStoryById(id: String): Flow<CachedStoryEntity?>

    @Query("UPDATE cached_stories SET isBookmarked = :bookmarked WHERE id = :storyId")
    suspend fun setBookmarked(storyId: String, bookmarked: Boolean)

    @Query("SELECT * FROM cached_stories WHERE isBookmarked = 1 ORDER BY publishedAt DESC")
    fun getBookmarkedStories(): Flow<List<CachedStoryEntity>>

    @Query(
        """SELECT * FROM cached_stories
        WHERE title LIKE '%' || :query || '%'
        OR summary LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
        LIMIT 100"""
    )
    fun searchStories(query: String): Flow<List<CachedStoryEntity>>

    @Query("DELETE FROM cached_stories WHERE isBookmarked = 0 AND expiresAt < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM cached_stories WHERE isBookmarked = 0")
    suspend fun clearCache()

    @Query("SELECT COUNT(*) FROM cached_stories")
    fun getStoryCount(): Flow<Int>

    @Query("SELECT * FROM cached_stories WHERE url = :normalizedUrl LIMIT 1")
    suspend fun findByUrl(normalizedUrl: String): CachedStoryEntity?
}
