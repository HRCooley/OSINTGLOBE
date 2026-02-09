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

/**
 * Central news aggregation repository. Fetches stories from four concurrent
 * sources (GDELT, GNews, NewsAPI, RSS) and applies deduplication, caching,
 * and geographic filtering.
 *
 * Data flow:
 * 1. Emit cached stories from Room (immediate, no network)
 * 2. Fetch fresh stories from all sources concurrently via coroutineScope/async
 * 3. Deduplicate by URL normalization + Jaccard title similarity (85% threshold)
 * 4. Cache with scope-based TTLs and clean up expired entries
 * 5. Fall back to embedded JSON (fallback_news.json) if all sources fail
 *
 * Geographic filtering:
 * - GDELT: uses "near:lat,lon within:500km" geographic query
 * - GNews/NewsAPI: country code derived from map center via bounding-box lookup
 * - RSS: feeds filtered by whether their coordinates are within view bounds
 *
 * Rate limiting:
 * - GNews and NewsAPI are limited to 100 requests per 24-hour sliding window
 * - Tracked via [RateLimitDao] in the Room database
 */
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

    /**
     * Fetches stories from all four sources concurrently. Each source runs in
     * its own async coroutine so network latency is parallelized. Individual
     * source failures are caught and return empty lists — the aggregator never
     * fails entirely unless all sources fail.
     */
    private suspend fun fetchFromAllSources(
        bounds: GeoBounds,
        scopes: Set<EditorialScope>
    ): List<NewsStory> = coroutineScope {
        val centerLat = (bounds.north + bounds.south) / 2
        val centerLon = (bounds.east + bounds.west) / 2

        // Determine country code for the viewing region
        val countryCode = getCountryCodeForLocation(centerLat, centerLon)

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
                val response = gNewsApi.getTopHeadlines(
                    apiKey = key,
                    country = countryCode
                )
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
                val response = newsApi.getTopHeadlines(
                    apiKey = key,
                    country = countryCode
                )
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
                .take(25)

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

    /**
     * Maps a lat/lon to a 2-letter country code for news API filtering.
     * Uses a bounding-box lookup for major countries supported by GNews/NewsAPI.
     * Returns null for ocean areas or unmapped regions (APIs will use global defaults).
     */
    private fun getCountryCodeForLocation(lat: Double, lon: Double): String? {
        // Country bounding boxes: (south, north, west, east) -> code
        // Covers countries supported by both GNews and NewsAPI
        val countries = listOf(
            // North America
            CountryBox(24.0, 50.0, -125.0, -66.0, "us"),
            CountryBox(41.0, 84.0, -141.0, -52.0, "ca"),
            CountryBox(14.0, 33.0, -118.0, -86.0, "mx"),
            // South America
            CountryBox(-34.0, 5.0, -74.0, -35.0, "br"),
            CountryBox(-56.0, -21.0, -74.0, -53.0, "ar"),
            CountryBox(-18.0, 13.0, -82.0, -60.0, "co"),
            // Europe
            CountryBox(49.0, 59.0, -8.0, 2.0, "gb"),
            CountryBox(42.0, 51.0, -5.0, 8.0, "fr"),
            CountryBox(47.0, 55.0, 6.0, 15.0, "de"),
            CountryBox(36.0, 47.0, 6.0, 19.0, "it"),
            CountryBox(36.0, 44.0, -10.0, 4.0, "es"),
            CountryBox(49.0, 55.0, 14.0, 24.0, "pl"),
            CountryBox(46.0, 49.0, 16.0, 23.0, "hu"),
            CountryBox(46.0, 56.0, 22.0, 40.0, "ua"),
            CountryBox(42.0, 45.0, 22.0, 29.0, "bg"),
            CountryBox(44.0, 48.0, 22.0, 30.0, "ro"),
            CountryBox(47.0, 50.0, 6.0, 10.0, "ch"),
            CountryBox(47.0, 56.0, 9.0, 17.0, "at"),
            CountryBox(50.0, 54.0, 3.0, 7.0, "be"),
            CountryBox(50.0, 54.0, 3.0, 8.0, "nl"),
            CountryBox(59.0, 70.0, 5.0, 31.0, "no"),
            CountryBox(55.0, 69.0, 11.0, 24.0, "se"),
            CountryBox(54.0, 72.0, 20.0, 180.0, "ru"),
            // Middle East
            CountryBox(36.0, 42.0, 26.0, 45.0, "tr"),
            CountryBox(29.0, 33.0, 34.0, 37.0, "il"),
            CountryBox(15.0, 32.0, 35.0, 55.0, "sa"),
            CountryBox(22.0, 32.0, 44.0, 50.0, "ae"),
            // Africa
            CountryBox(22.0, 32.0, 25.0, 37.0, "eg"),
            CountryBox(4.0, 14.0, 3.0, 15.0, "ng"),
            CountryBox(-35.0, -22.0, 16.0, 33.0, "za"),
            CountryBox(-2.0, 5.0, 33.0, 42.0, "ke"),
            CountryBox(30.0, 38.0, -10.0, -1.0, "ma"),
            // Asia
            CountryBox(8.0, 37.0, 68.0, 98.0, "in"),
            CountryBox(18.0, 54.0, 73.0, 135.0, "cn"),
            CountryBox(24.0, 46.0, 123.0, 146.0, "jp"),
            CountryBox(33.0, 39.0, 124.0, 130.0, "kr"),
            CountryBox(-11.0, 6.0, 95.0, 141.0, "id"),
            CountryBox(5.0, 21.0, 97.0, 106.0, "th"),
            CountryBox(1.0, 7.0, 100.0, 120.0, "sg"),
            CountryBox(1.0, 8.0, 100.0, 120.0, "my"),
            CountryBox(5.0, 19.0, 117.0, 127.0, "ph"),
            CountryBox(23.0, 26.0, 120.0, 122.0, "tw"),
            CountryBox(22.0, 23.0, 113.0, 115.0, "hk"),
            CountryBox(23.0, 42.0, 44.0, 63.0, "pk"),
            // Oceania
            CountryBox(-44.0, -10.0, 113.0, 154.0, "au"),
            CountryBox(-47.0, -34.0, 166.0, 179.0, "nz"),
        )

        for (c in countries) {
            if (lat in c.south..c.north && lon in c.west..c.east) {
                return c.code
            }
        }
        return null // Ocean or unmapped region — APIs return global results
    }

    private data class CountryBox(
        val south: Double, val north: Double,
        val west: Double, val east: Double,
        val code: String
    )

    companion object {
        /**
         * Two-pass deduplication:
         * 1. URL-based — stories with the same normalized URL are merged
         * 2. Title-based — stories with >85% Jaccard similarity published
         *    within the same hour are considered duplicates (catches same-event
         *    coverage from different outlets with different URLs)
         *
         * Merged stories combine source attributions and fill in missing fields
         * (summary, image, sentiment) from the duplicate.
         */
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
