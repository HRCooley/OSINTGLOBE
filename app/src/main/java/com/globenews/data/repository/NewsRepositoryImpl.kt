package com.globenews.data.repository

import com.globenews.core.common.Constants
import com.globenews.core.common.jaccardSimilarity
import com.globenews.core.common.normalizeUrl
import com.globenews.core.database.dao.RateLimitDao
import com.globenews.core.database.dao.StoryDao
import com.globenews.core.database.entity.RateLimitEntity
import com.globenews.data.mapper.EntityMapper
import com.globenews.data.mapper.GdeltMapper
import com.globenews.data.mapper.GNewsMapper
import com.globenews.data.mapper.NewsApiMapper
import com.globenews.data.source.local.FallbackNewsLoader
import com.globenews.data.source.remote.gdelt.GdeltApiService
import com.globenews.data.source.remote.gnews.GNewsApiService
import com.globenews.data.source.remote.newsapi.NewsApiService
import com.globenews.data.source.remote.rss.RssFeedLoader
import com.globenews.data.source.remote.rss.RssParser
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.repository.NewsRepository
import com.globenews.plugin.GeoBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val storyDao: StoryDao,
    private val rateLimitDao: RateLimitDao,
    private val gdeltApi: GdeltApiService,
    private val gNewsApi: GNewsApiService,
    private val newsApi: NewsApiService,
    private val rssFeedLoader: RssFeedLoader,
    private val fallbackLoader: FallbackNewsLoader,
    private val okHttpClient: OkHttpClient,
    private val apiKeys: ApiKeyProvider
) : NewsRepository {

    private val rssParser = RssParser()

    override fun getStoriesByRegion(
        bounds: GeoBounds,
        scopes: Set<EditorialScope>,
        forceRefresh: Boolean
    ): Flow<Result<List<NewsStory>>> = flow {
        val scopeStrings = scopes.map { it.name }
        val now = System.currentTimeMillis()

        // First emit cached data
        val cachedEntities = storyDao.getStoriesByRegion(
            north = bounds.north, south = bounds.south,
            east = bounds.east, west = bounds.west,
            scopes = scopeStrings, now = now
        )
        var emittedCache = false

        cachedEntities.collect { entities ->
            if (!emittedCache || !forceRefresh) {
                val stories = entities.map { EntityMapper.entityToDomain(it) }
                if (stories.isNotEmpty()) {
                    emit(Result.success(stories))
                    emittedCache = true
                }
            }

            if (!emittedCache || forceRefresh) {
                // Fetch from remote sources
                try {
                    val freshStories = fetchFromAllSources(bounds, scopes)
                    val deduped = deduplicateStories(freshStories)

                    // Cache stories
                    val entitiesToCache = deduped.map { story ->
                        val ttl = ttlForScope(story.scope)
                        EntityMapper.domainToEntity(story, ttl)
                    }
                    storyDao.insertAll(entitiesToCache)
                    storyDao.deleteExpired(System.currentTimeMillis())

                    emit(Result.success(deduped))
                } catch (e: Exception) {
                    if (!emittedCache) {
                        // Load fallback if no cache and remote fails
                        val fallback = fallbackLoader.loadFallbackStories()
                            .filter { it.scope in scopes }
                            .filter { isInBounds(it, bounds) }
                        if (fallback.isNotEmpty()) {
                            emit(Result.success(fallback))
                        } else {
                            emit(Result.failure(e))
                        }
                    }
                }
                emittedCache = true
                return@collect
            }
        }
    }

    private suspend fun fetchFromAllSources(
        bounds: GeoBounds,
        scopes: Set<EditorialScope>
    ): List<NewsStory> = coroutineScope {
        val centerLat = (bounds.north + bounds.south) / 2
        val centerLon = (bounds.east + bounds.west) / 2

        val gdeltDeferred = async {
            try {
                val query = buildGdeltQuery(bounds)
                val response = gdeltApi.searchArticles(query = query)
                response.articles?.map { article ->
                    GdeltMapper.toDomain(article, centerLat, centerLon, null, null)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        val gNewsDeferred = async {
            if (!canMakeRequest("gnews", 100, 24 * 60 * 60 * 1000L)) return@async emptyList()
            val key = apiKeys.gNewsKey
            if (key.isBlank()) return@async emptyList()
            try {
                incrementRequestCount("gnews")
                val response = gNewsApi.getTopHeadlines(apiKey = key)
                response.articles?.map { article ->
                    GNewsMapper.toDomain(article, centerLat, centerLon, null, null)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        val newsApiDeferred = async {
            if (!canMakeRequest("newsapi", 100, 24 * 60 * 60 * 1000L)) return@async emptyList()
            val key = apiKeys.newsApiKey
            if (key.isBlank()) return@async emptyList()
            try {
                incrementRequestCount("newsapi")
                val response = newsApi.getTopHeadlines(apiKey = key)
                response.articles?.mapNotNull { article ->
                    NewsApiMapper.toDomain(article, centerLat, centerLon, null, null)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        val rssDeferred = async {
            fetchRssFeeds(bounds)
        }

        val allStories = mutableListOf<NewsStory>()
        allStories.addAll(gdeltDeferred.await())
        allStories.addAll(gNewsDeferred.await())
        allStories.addAll(newsApiDeferred.await())
        allStories.addAll(rssDeferred.await())
        allStories
    }

    private suspend fun fetchRssFeeds(bounds: GeoBounds): List<NewsStory> =
        withContext(Dispatchers.IO) {
            val feeds = rssFeedLoader.loadFeedConfigs()
                .filter { isInBounds(it.lat, it.lon, bounds) }
                .take(10)

            feeds.flatMap { feed ->
                try {
                    val request = Request.Builder().url(feed.url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val xml = response.body?.string() ?: return@flatMap emptyList()
                    rssParser.parse(xml, feed)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    override fun getStoryById(id: String): Flow<NewsStory?> {
        return storyDao.getStoryById(id).map { entity ->
            entity?.let { EntityMapper.entityToDomain(it) }
        }
    }

    override suspend fun bookmarkStory(storyId: String, bookmarked: Boolean) {
        storyDao.setBookmarked(storyId, bookmarked)
    }

    override fun getBookmarkedStories(): Flow<List<NewsStory>> {
        return storyDao.getBookmarkedStories().map { entities ->
            entities.map { EntityMapper.entityToDomain(it) }
        }
    }

    override fun searchStories(query: String): Flow<List<NewsStory>> {
        return storyDao.searchStories(query).map { entities ->
            entities.map { EntityMapper.entityToDomain(it) }
        }
    }

    override suspend fun clearCache() {
        storyDao.clearCache()
    }

    override fun getCacheSizeBytes(): Flow<Long> {
        return storyDao.getStoryCount().map { count ->
            count * 1024L // rough estimate
        }
    }

    private fun buildGdeltQuery(bounds: GeoBounds): String {
        val lat = (bounds.north + bounds.south) / 2
        val lon = (bounds.east + bounds.west) / 2
        return "near:${lat},${lon} within:500km"
    }

    private suspend fun canMakeRequest(sourceId: String, maxRequests: Int, windowMs: Long): Boolean {
        val rateLimit = rateLimitDao.getRateLimit(sourceId) ?: return true
        val now = System.currentTimeMillis()
        if (now - rateLimit.windowStartMillis > rateLimit.windowDurationMillis) {
            rateLimitDao.upsert(
                RateLimitEntity(sourceId, 0, maxRequests, now, windowMs)
            )
            return true
        }
        return rateLimit.requestCount < rateLimit.maxRequests
    }

    private suspend fun incrementRequestCount(sourceId: String) {
        val existing = rateLimitDao.getRateLimit(sourceId)
        if (existing == null) {
            rateLimitDao.upsert(
                RateLimitEntity(sourceId, 1, 100, System.currentTimeMillis(), 24 * 60 * 60 * 1000L)
            )
        } else {
            rateLimitDao.incrementCount(sourceId)
        }
    }

    private fun ttlForScope(scope: EditorialScope): Long {
        return when (scope) {
            EditorialScope.INTERNATIONAL -> Constants.CACHE_TTL_INTERNATIONAL_HOURS
            EditorialScope.NATIONAL -> Constants.CACHE_TTL_NATIONAL_HOURS
            EditorialScope.REGIONAL -> Constants.CACHE_TTL_REGIONAL_HOURS
            EditorialScope.LOCAL -> Constants.CACHE_TTL_LOCAL_HOURS
        }
    }

    private fun isInBounds(story: NewsStory, bounds: GeoBounds): Boolean {
        return isInBounds(story.location.latitude, story.location.longitude, bounds)
    }

    private fun isInBounds(lat: Double, lon: Double, bounds: GeoBounds): Boolean {
        return lat in bounds.south..bounds.north && lon in bounds.west..bounds.east
    }

    companion object {
        fun deduplicateStories(stories: List<NewsStory>): List<NewsStory> {
            val seen = mutableMapOf<String, NewsStory>()
            val result = mutableListOf<NewsStory>()

            for (story in stories) {
                val normalizedUrl = story.url.normalizeUrl()

                // Check URL-based dedup
                if (normalizedUrl in seen) {
                    val existing = seen[normalizedUrl]!!
                    seen[normalizedUrl] = mergeStories(existing, story)
                    continue
                }

                // Check title similarity
                var isDuplicate = false
                for ((_, existing) in seen) {
                    val similarity = jaccardSimilarity(story.title, existing.title)
                    if (similarity > 0.85) {
                        val sameHour = kotlin.math.abs(
                            story.publishedAt.epochSecond - existing.publishedAt.epochSecond
                        ) < 3600
                        if (sameHour) {
                            seen[existing.url.normalizeUrl()] = mergeStories(existing, story)
                            isDuplicate = true
                            break
                        }
                    }
                }

                if (!isDuplicate) {
                    seen[normalizedUrl] = story
                    result.add(story)
                }
            }

            return seen.values.toList().sortedByDescending { it.publishedAt }
        }

        private fun mergeStories(existing: NewsStory, newStory: NewsStory): NewsStory {
            val mergedSources = (existing.sources + newStory.sources).distinctBy {
                "${it.name}-${it.providerApi}"
            }
            return existing.copy(
                sources = mergedSources,
                summary = existing.summary ?: newStory.summary,
                imageUrl = existing.imageUrl ?: newStory.imageUrl,
                sentiment = existing.sentiment ?: newStory.sentiment
            )
        }
    }
}

interface ApiKeyProvider {
    val gNewsKey: String
    val newsApiKey: String
    val mediaStackKey: String
    val cesiumIonToken: String
}
