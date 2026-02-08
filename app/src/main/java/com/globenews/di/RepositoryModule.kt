package com.globenews.di

import com.globenews.BuildConfig
import com.globenews.data.repository.ApiKeyProvider
import com.globenews.data.repository.LocationRepositoryImpl
import com.globenews.data.repository.NewsRepositoryImpl
import com.globenews.domain.repository.LocationRepository
import com.globenews.domain.repository.NewsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    companion object {
        @Provides
        @Singleton
        fun provideApiKeyProvider(): ApiKeyProvider {
            return object : ApiKeyProvider {
                override val gNewsKey: String get() = BuildConfig.GNEWS_API_KEY
                override val newsApiKey: String get() = BuildConfig.NEWSAPI_KEY
                override val mediaStackKey: String get() = BuildConfig.MEDIASTACK_KEY
                override val cesiumIonToken: String get() = BuildConfig.CESIUM_ION_TOKEN
            }
        }
    }
}
