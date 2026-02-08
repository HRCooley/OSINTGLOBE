package com.globenews.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.globenews.core.database.dao.RateLimitDao
import com.globenews.core.database.dao.StoryDao
import com.globenews.core.database.entity.CachedStoryEntity
import com.globenews.core.database.entity.RateLimitEntity

@Database(
    entities = [
        CachedStoryEntity::class,
        RateLimitEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GlobeNewsDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun rateLimitDao(): RateLimitDao
}
