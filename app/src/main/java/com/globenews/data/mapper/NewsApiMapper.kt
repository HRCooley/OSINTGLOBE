package com.globenews.data.mapper

import com.globenews.data.source.remote.newsapi.NewsApiArticle
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import java.time.Instant
import java.util.UUID

object NewsApiMapper {
    fun toDomain(
        article: NewsApiArticle,
        latitude: Double,
        longitude: Double,
        placeName: String?,
        countryCode: String?
    ): NewsStory? {
        val title = article.title ?: return null

        val publishedAt = try {
            Instant.parse(article.publishedAt ?: return null)
        } catch (e: Exception) {
            Instant.now()
        }

        return NewsStory(
            id = UUID.nameUUIDFromBytes(article.url.toByteArray()).toString(),
            title = title,
            summary = article.description,
            url = article.url,
            imageUrl = article.urlToImage,
            publishedAt = publishedAt,
            location = StoryLocation(
                latitude = latitude,
                longitude = longitude,
                placeName = placeName,
                countryCode = countryCode,
                admin1 = null
            ),
            scope = EditorialScope.NATIONAL,
            sources = listOf(
                SourceAttribution(
                    name = article.source.name ?: "NewsAPI",
                    providerApi = "newsapi",
                    retrievedAt = Instant.now()
                )
            ),
            language = "en",
            sentiment = null,
            categories = emptyList()
        )
    }
}
