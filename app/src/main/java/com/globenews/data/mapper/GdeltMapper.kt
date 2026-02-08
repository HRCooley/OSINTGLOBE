package com.globenews.data.mapper

import com.globenews.data.source.remote.gdelt.GdeltArticle
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

object GdeltMapper {
    private val gdeltDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun toDomain(
        article: GdeltArticle,
        latitude: Double,
        longitude: Double,
        placeName: String?,
        countryCode: String?
    ): NewsStory {
        val publishedAt = parseGdeltDate(article.seenDate)
        val sentiment = parseTone(article.tone)

        return NewsStory(
            id = UUID.nameUUIDFromBytes(article.url.toByteArray()).toString(),
            title = article.title,
            summary = null,
            url = article.url,
            imageUrl = article.socialImage?.takeIf { it.isNotBlank() },
            publishedAt = publishedAt,
            location = StoryLocation(
                latitude = latitude,
                longitude = longitude,
                placeName = placeName,
                countryCode = countryCode ?: article.sourceCountry,
                admin1 = null
            ),
            scope = inferScope(countryCode),
            sources = listOf(
                SourceAttribution(
                    name = article.domain ?: "GDELT",
                    providerApi = "gdelt",
                    retrievedAt = Instant.now()
                )
            ),
            language = article.language ?: "en",
            sentiment = sentiment,
            categories = emptyList()
        )
    }

    private fun parseGdeltDate(dateStr: String): Instant {
        return try {
            Instant.from(gdeltDateFormat.parse(dateStr))
        } catch (e: DateTimeParseException) {
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                Instant.now()
            }
        }
    }

    private fun parseTone(tone: String?): Float? {
        if (tone.isNullOrBlank()) return null
        return try {
            val value = tone.split(",").firstOrNull()?.toFloat() ?: return null
            (value / 10f).coerceIn(-1f, 1f)
        } catch (e: Exception) {
            null
        }
    }

    private fun inferScope(countryCode: String?): EditorialScope {
        return if (countryCode.isNullOrBlank()) {
            EditorialScope.INTERNATIONAL
        } else {
            EditorialScope.NATIONAL
        }
    }
}
