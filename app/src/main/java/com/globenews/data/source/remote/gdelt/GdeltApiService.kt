package com.globenews.data.source.remote.gdelt

import retrofit2.http.GET
import retrofit2.http.Query

interface GdeltApiService {
    @GET("doc/doc")
    suspend fun searchArticles(
        @Query("query") query: String,
        @Query("mode") mode: String = "ArtList",
        @Query("maxrecords") maxRecords: Int = 75,
        @Query("format") format: String = "json",
        @Query("timespan") timespan: String = "24h",
        @Query("sort") sort: String = "DateDesc"
    ): GdeltResponse
}
