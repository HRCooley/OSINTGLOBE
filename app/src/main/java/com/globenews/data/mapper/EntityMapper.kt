package com.globenews.data.mapper

import com.globenews.core.database.entity.CachedStoryEntity
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

object EntityMapper {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val sourceListType = Types.newParameterizedType(
        List::class.java, SourceAttributionJson::class.java
    )
    private val sourceListAdapter = moshi.adapter<List<SourceAttributionJson>>(sourceListType)
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    data class SourceAttributionJson(
        val name: String,
        val providerApi: String,
        val retrievedAt: Long
    )

    fun entityToDomain(entity: CachedStoryEntity): NewsStory {
        val sources = try {
            sourceListAdapter.fromJson(entity.sourcesJson)?.map {
                SourceAttribution(it.name, it.providerApi, Instant.ofEpochMilli(it.retrievedAt))
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val categories = try {
            stringListAdapter.fromJson(entity.categoriesJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return NewsStory(
            id = entity.id,
            title = entity.title,
            summary = entity.summary,
            url = entity.url,
            imageUrl = entity.imageUrl,
            publishedAt = Instant.ofEpochMilli(entity.publishedAt),
            location = StoryLocation(
                latitude = entity.latitude,
                longitude = entity.longitude,
                placeName = entity.placeName,
                countryCode = entity.countryCode,
                admin1 = entity.admin1
            ),
            scope = try {
                EditorialScope.valueOf(entity.scope)
            } catch (e: Exception) {
                EditorialScope.NATIONAL
            },
            sources = sources,
            language = entity.language,
            sentiment = entity.sentiment,
            categories = categories,
            isBookmarked = entity.isBookmarked
        )
    }

    fun domainToEntity(story: NewsStory, ttlHours: Long): CachedStoryEntity {
        val sourcesJson = sourceListAdapter.toJson(
            story.sources.map {
                SourceAttributionJson(it.name, it.providerApi, it.retrievedAt.toEpochMilli())
            }
        )
        val categoriesJson = stringListAdapter.toJson(story.categories)
        val now = System.currentTimeMillis()

        return CachedStoryEntity(
            id = story.id,
            title = story.title,
            summary = story.summary,
            url = story.url,
            imageUrl = story.imageUrl,
            publishedAt = story.publishedAt.toEpochMilli(),
            latitude = story.location.latitude,
            longitude = story.location.longitude,
            placeName = story.location.placeName,
            countryCode = story.location.countryCode,
            admin1 = story.location.admin1,
            scope = story.scope.name,
            sourcesJson = sourcesJson,
            language = story.language,
            sentiment = story.sentiment,
            categoriesJson = categoriesJson,
            isBookmarked = story.isBookmarked,
            cachedAt = now,
            expiresAt = now + ttlHours * 3600 * 1000
        )
    }
}
