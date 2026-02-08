package com.globenews.di

import com.globenews.core.common.Constants
import com.globenews.core.network.UserAgentInterceptor
import com.globenews.data.source.geocoding.NominatimService
import com.globenews.data.source.remote.gdelt.GdeltApiService
import com.globenews.data.source.remote.gnews.GNewsApiService
import com.globenews.data.source.remote.newsapi.NewsApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGdeltApi(client: OkHttpClient, moshi: Moshi): GdeltApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.GDELT_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(GdeltApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideGNewsApi(client: OkHttpClient, moshi: Moshi): GNewsApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.GNEWS_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GNewsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNewsApi(client: OkHttpClient, moshi: Moshi): NewsApiService {
        return Retrofit.Builder()
            .baseUrl(Constants.NEWSAPI_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NewsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNominatimService(client: OkHttpClient, moshi: Moshi): NominatimService {
        return Retrofit.Builder()
            .baseUrl(Constants.NOMINATIM_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NominatimService::class.java)
    }
}
