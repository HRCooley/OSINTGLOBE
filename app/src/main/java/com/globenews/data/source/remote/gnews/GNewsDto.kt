package com.globenews.data.source.remote.gnews

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GNewsResponse(
    @Json(name = "totalArticles") val totalArticles: Int? = null,
    @Json(name = "articles") val articles: List<GNewsArticle>? = null
)

@JsonClass(generateAdapter = true)
data class GNewsArticle(
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "content") val content: String? = null,
    @Json(name = "url") val url: String,
    @Json(name = "image") val image: String? = null,
    @Json(name = "publishedAt") val publishedAt: String,
    @Json(name = "source") val source: GNewsSource
)

@JsonClass(generateAdapter = true)
data class GNewsSource(
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String? = null
)
