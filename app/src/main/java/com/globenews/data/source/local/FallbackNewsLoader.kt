package com.globenews.data.source.local

import android.content.Context
import com.globenews.core.common.Constants
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class FallbackStoryJson(
    @Json(name = "title") val title: String,
    @Json(name = "summary") val summary: String?,
    @Json(name = "url") val url: String,
    @Json(name = "imageUrl") val imageUrl: String? = null,
    @Json(name = "publishedAt") val publishedAt: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "placeName") val placeName: String?,
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "admin1") val admin1: String? = null,
    @Json(name = "scope") val scope: String,
    @Json(name = "sourceName") val sourceName: String,
    @Json(name = "language") val language: String,
    @Json(name = "categories") val categories: List<String> = emptyList()
)

@Singleton
class FallbackNewsLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun loadFallbackStories(): List<NewsStory> {
        return try {
            val json = context.assets.open(Constants.FALLBACK_NEWS_FILE)
                .bufferedReader()
                .use { it.readText() }

            val listType = Types.newParameterizedType(List::class.java, FallbackStoryJson::class.java)
            val adapter = moshi.adapter<List<FallbackStoryJson>>(listType)
            val fallbackStories = adapter.fromJson(json) ?: emptyList()

            fallbackStories.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun FallbackStoryJson.toDomain(): NewsStory {
        return NewsStory(
            id = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
            title = title,
            summary = summary,
            url = url,
            imageUrl = imageUrl,
            publishedAt = try {
                Instant.parse(publishedAt)
            } catch (e: Exception) {
                Instant.now()
            },
            location = StoryLocation(
                latitude = latitude,
                longitude = longitude,
                placeName = placeName,
                countryCode = countryCode,
                admin1 = admin1
            ),
            scope = try {
                EditorialScope.valueOf(scope.uppercase())
            } catch (e: Exception) {
                EditorialScope.NATIONAL
            },
            sources = listOf(
                SourceAttribution(
                    name = sourceName,
                    providerApi = "fallback",
                    retrievedAt = Instant.now()
                )
            ),
            language = language,
            sentiment = null,
            categories = categories
        )
    }
}
