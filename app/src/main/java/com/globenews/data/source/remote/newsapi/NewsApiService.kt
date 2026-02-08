package com.globenews.data.source.remote.newsapi

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NewsApiService {
    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Header("X-Api-Key") apiKey: String,
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("pageSize") pageSize: Int = 20
    ): NewsApiResponse

    @GET("everything")
    suspend fun searchEverything(
        @Header("X-Api-Key") apiKey: String,
        @Query("q") query: String,
        @Query("sortBy") sortBy: String = "publishedAt",
        @Query("pageSize") pageSize: Int = 20
    ): NewsApiResponse
}
