package com.globenews.data.source.remote.gdelt

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GdeltResponse(
    @Json(name = "articles") val articles: List<GdeltArticle>? = null
)

@JsonClass(generateAdapter = true)
data class GdeltArticle(
    @Json(name = "url") val url: String,
    @Json(name = "url_mobile") val urlMobile: String? = null,
    @Json(name = "title") val title: String,
    @Json(name = "seendate") val seenDate: String,
    @Json(name = "socialimage") val socialImage: String? = null,
    @Json(name = "domain") val domain: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "sourcecountry") val sourceCountry: String? = null,
    @Json(name = "tone") val tone: String? = null
)
