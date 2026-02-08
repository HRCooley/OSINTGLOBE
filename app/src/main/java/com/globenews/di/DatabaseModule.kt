package com.globenews.di

import android.content.Context
import androidx.room.Room
import com.globenews.core.common.Constants
import com.globenews.core.database.GlobeNewsDatabase
import com.globenews.core.database.dao.RateLimitDao
import com.globenews.core.database.dao.StoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GlobeNewsDatabase {
        return Room.databaseBuilder(
            context,
            GlobeNewsDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideStoryDao(database: GlobeNewsDatabase): StoryDao {
        return database.storyDao()
    }

    @Provides
    fun provideRateLimitDao(database: GlobeNewsDatabase): RateLimitDao {
        return database.rateLimitDao()
    }
}
