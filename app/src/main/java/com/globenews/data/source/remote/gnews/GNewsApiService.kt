package com.globenews.data.source.remote.gnews

import retrofit2.http.GET
import retrofit2.http.Query

interface GNewsApiService {
    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("token") apiKey: String,
        @Query("lang") language: String = "en",
        @Query("country") country: String? = null,
        @Query("max") max: Int = 10
    ): GNewsResponse

    @GET("search")
    suspend fun search(
        @Query("token") apiKey: String,
        @Query("q") query: String,
        @Query("lang") language: String = "en",
        @Query("max") max: Int = 10
    ): GNewsResponse
}
