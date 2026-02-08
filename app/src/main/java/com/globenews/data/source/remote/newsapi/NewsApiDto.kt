package com.globenews.data.source.remote.newsapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NewsApiResponse(
    @Json(name = "status") val status: String,
    @Json(name = "totalResults") val totalResults: Int? = null,
    @Json(name = "articles") val articles: List<NewsApiArticle>? = null
)

@JsonClass(generateAdapter = true)
data class NewsApiArticle(
    @Json(name = "source") val source: NewsApiSource,
    @Json(name = "author") val author: String? = null,
    @Json(name = "title") val title: String?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "url") val url: String,
    @Json(name = "urlToImage") val urlToImage: String? = null,
    @Json(name = "publishedAt") val publishedAt: String? = null,
    @Json(name = "content") val content: String? = null
)

@JsonClass(generateAdapter = true)
data class NewsApiSource(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null
)
