package com.globenews.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.globenews.core.database.entity.RateLimitEntity

@Dao
interface RateLimitDao {
    @Query("SELECT * FROM rate_limits WHERE sourceId = :sourceId")
    suspend fun getRateLimit(sourceId: String): RateLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rateLimit: RateLimitEntity)

    @Query("UPDATE rate_limits SET requestCount = requestCount + 1 WHERE sourceId = :sourceId")
    suspend fun incrementCount(sourceId: String)

    @Query("DELETE FROM rate_limits")
    suspend fun clearAll()
}
