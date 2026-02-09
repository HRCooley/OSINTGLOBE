package com.globenews.data.repository

import android.util.Log
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    // Cache for global grid results to avoid re-querying on every camera move
    private var globalGridCache: List<NewsStory> = emptyList()
    private var globalGridCacheTimestamp: Long = 0L

    override fun getStoriesByRegion(
        bounds: GeoBounds,
        scopes: Set<EditorialScope>,
        altitudeKm: Double,
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
                    val freshStories = fetchFromAllSources(bounds, scopes, altitudeKm)
                    val deduped = deduplicateStories(freshStories)

                    // Log coverage stats
                    logCoverageStats(freshStories, deduped)

                    // Cache stories
                    val entitiesToCache = deduped.map { story ->
                        val ttl = ttlForScope(story.scope)
                        EntityMapper.domainToEntity(story, ttl)
                    }
                    storyDao.insertAll(entitiesToCache)
                    storyDao.deleteExpired(System.currentTimeMillis())

                    emit(Result.success(deduped))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch stories", e)
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
        scopes: Set<EditorialScope>,
        altitudeKm: Double
    ): List<NewsStory> = coroutineScope {
        val centerLat = (bounds.north + bounds.south) / 2
        val centerLon = (bounds.east + bounds.west) / 2

        val gdeltDeferred = async {
            fetchGdeltStories(bounds, altitudeKm, centerLat, centerLon)
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
                Log.w(TAG, "GNews fetch failed", e)
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
                Log.w(TAG, "NewsAPI fetch failed", e)
                emptyList()
            }
        }

        val rssDeferred = async {
            fetchRssFeeds(bounds, altitudeKm)
        }

        val allStories = mutableListOf<NewsStory>()
        allStories.addAll(gdeltDeferred.await())
        allStories.addAll(gNewsDeferred.await())
        allStories.addAll(newsApiDeferred.await())
        allStories.addAll(rssDeferred.await())

        Log.d(TAG, "Fetched stories - GDELT: ${gdeltDeferred.await().size}, " +
                "GNews: ${gNewsDeferred.await().size}, " +
                "NewsAPI: ${newsApiDeferred.await().size}, " +
                "RSS: ${rssDeferred.await().size}, " +
                "Total before dedup: ${allStories.size}")

        allStories
    }

    // ---- GDELT: altitude-aware query strategy ----

    private suspend fun fetchGdeltStories(
        bounds: GeoBounds,
        altitudeKm: Double,
        centerLat: Double,
        centerLon: Double
    ): List<NewsStory> {
        return try {
            when {
                altitudeKm > 8_000 -> fetchGdeltGlobalGrid()
                altitudeKm > 1_000 -> fetchGdeltSubQueries(bounds)
                else -> fetchGdeltSingle(centerLat, centerLon, altitudeKm)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GDELT fetch failed", e)
            emptyList()
        }
    }

    /**
     * High altitude (>8000km): Fire grid of regional queries covering the entire globe.
     * Results are cached for 15 minutes to avoid re-querying on every camera move.
     */
    private suspend fun fetchGdeltGlobalGrid(): List<NewsStory> = coroutineScope {
        val now = System.currentTimeMillis()
        if (globalGridCache.isNotEmpty() &&
            (now - globalGridCacheTimestamp) < GLOBAL_GRID_CACHE_MS
        ) {
            Log.d(TAG, "Using cached global grid results: ${globalGridCache.size} stories")
            return@coroutineScope globalGridCache
        }

        val allStories = mutableListOf<NewsStory>()

        // Process in batches of 5 with 500ms delay between batches
        val batches = GLOBAL_GRID.chunked(GDELT_BATCH_SIZE)
        for (batch in batches) {
            val batchResults = batch.map { region ->
                async {
                    try {
                        val query = "near:${region.lat},${region.lon} ${region.radiusKm}km"
                        val response = gdeltApi.searchArticles(
                            query = query,
                            maxRecords = GDELT_GRID_MAX_RECORDS
                        )
                        response.articles?.map { article ->
                            GdeltMapper.toDomain(article, region.lat, region.lon, region.name, null)
                        } ?: emptyList()
                    } catch (e: Exception) {
                        Log.w(TAG, "GDELT grid query failed for ${region.name}", e)
                        emptyList()
                    }
                }
            }.awaitAll()

            batchResults.forEach { allStories.addAll(it) }

            // Delay between batches to avoid throttling
            if (batch !== batches.last()) {
                delay(GDELT_BATCH_DELAY_MS)
            }
        }

        Log.d(TAG, "GDELT global grid fetched ${allStories.size} stories across ${GLOBAL_GRID.size} regions")

        // Deduplicate by URL within GDELT results
        val deduped = allStories.distinctBy { it.url.normalizeUrl() }
        globalGridCache = deduped
        globalGridCacheTimestamp = now
        deduped
    }

    /**
     * Mid altitude (1000-8000km): Fire 4 sub-queries spread across the visible bounding box.
     */
    private suspend fun fetchGdeltSubQueries(bounds: GeoBounds): List<NewsStory> = coroutineScope {
        val subPoints = generateSubQueryPoints(bounds)
        val radiusKm = ((bounds.north - bounds.south) * 111 / 4).toInt().coerceIn(200, 2000)

        val results = subPoints.map { (lat, lon) ->
            async {
                try {
                    val query = "near:${lat},${lon} ${radiusKm}km"
                    val response = gdeltApi.searchArticles(
                        query = query,
                        maxRecords = GDELT_SUB_MAX_RECORDS
                    )
                    response.articles?.map { article ->
                        GdeltMapper.toDomain(article, lat, lon, null, null)
                    } ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "GDELT sub-query failed for ($lat, $lon)", e)
                    emptyList()
                }
            }
        }.awaitAll()

        val allStories = results.flatten()
        Log.d(TAG, "GDELT sub-queries fetched ${allStories.size} stories from ${subPoints.size} points")
        allStories.distinctBy { it.url.normalizeUrl() }
    }

    /**
     * Low altitude (<1000km): Single focused query.
     */
    private suspend fun fetchGdeltSingle(
        centerLat: Double,
        centerLon: Double,
        altitudeKm: Double
    ): List<NewsStory> {
        val radiusKm = (altitudeKm * 0.5).toInt().coerceIn(50, 500)
        val query = "near:${centerLat},${centerLon} ${radiusKm}km"
        val response = gdeltApi.searchArticles(query = query, maxRecords = 75)
        return response.articles?.map { article ->
            GdeltMapper.toDomain(article, centerLat, centerLon, null, null)
        } ?: emptyList()
    }

    private fun generateSubQueryPoints(bounds: GeoBounds): List<Pair<Double, Double>> {
        val latStep = (bounds.north - bounds.south) / 2
        val lonStep = (bounds.east - bounds.west) / 2
        return listOf(
            (bounds.south + latStep * 0.5) to (bounds.west + lonStep * 0.5),
            (bounds.south + latStep * 0.5) to (bounds.west + lonStep * 1.5),
            (bounds.south + latStep * 1.5) to (bounds.west + lonStep * 0.5),
            (bounds.south + latStep * 1.5) to (bounds.west + lonStep * 1.5),
        )
    }

    // ---- RSS: altitude-aware feed selection ----

    private suspend fun fetchRssFeeds(bounds: GeoBounds, altitudeKm: Double): List<NewsStory> =
        withContext(Dispatchers.IO) {
            val allFeeds = rssFeedLoader.loadFeedConfigs()

            // At high altitude, include ALL international feeds + bounded feeds
            // At lower altitudes, include feeds in bounds
            val feeds = if (altitudeKm > 8_000) {
                val international = allFeeds.filter {
                    it.scope.equals("INTERNATIONAL", ignoreCase = true)
                }
                val bounded = allFeeds.filter {
                    !it.scope.equals("INTERNATIONAL", ignoreCase = true) &&
                            isInBounds(it.lat, it.lon, bounds)
                }
                (international + bounded).distinctBy { it.url }.take(30)
            } else if (altitudeKm > 1_000) {
                allFeeds.filter { isInBounds(it.lat, it.lon, bounds) }.take(20)
            } else {
                allFeeds.filter { isInBounds(it.lat, it.lon, bounds) }.take(15)
            }

            Log.d(TAG, "Fetching ${feeds.size} RSS feeds (altitude: ${altitudeKm.toInt()}km)")

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

    // ---- Standard repository methods ----

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
        globalGridCache = emptyList()
        globalGridCacheTimestamp = 0L
    }

    override fun getCacheSizeBytes(): Flow<Long> {
        return storyDao.getStoryCount().map { count ->
            count * 1024L // rough estimate
        }
    }

    // ---- Helper methods ----

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

    private fun logCoverageStats(raw: List<NewsStory>, deduped: List<NewsStory>) {
        val bySource = raw.groupBy { it.sources.firstOrNull()?.providerApi ?: "unknown" }
            .mapValues { it.value.size }
        Log.d(TAG, "Fetched stories - ${bySource.entries.joinToString { "${it.key}: ${it.value}" }}, " +
                "Total after dedup: ${deduped.size}")

        // Coverage by approximate region
        val regionCounts = mutableMapOf(
            "Africa" to 0, "Americas" to 0, "Asia" to 0,
            "Europe" to 0, "Middle East" to 0, "Oceania" to 0
        )
        for (story in deduped) {
            val lat = story.location.latitude
            val lon = story.location.longitude
            val region = when {
                lat in -35.0..37.0 && lon in -20.0..55.0 -> "Africa"
                lat in -56.0..72.0 && lon in -170.0..-30.0 -> "Americas"
                lat in 25.0..45.0 && lon in 25.0..65.0 -> "Middle East"
                lat in -10.0..55.0 && lon in 65.0..180.0 -> "Asia"
                lat in -50.0..0.0 && lon in 100.0..180.0 -> "Oceania"
                lat in 35.0..72.0 && lon in -25.0..65.0 -> "Europe"
                else -> "Other"
            }
            if (region in regionCounts) {
                regionCounts[region] = regionCounts[region]!! + 1
            }
        }
        Log.d(TAG, "Coverage by region - ${regionCounts.entries.joinToString { "${it.key}: ${it.value}" }}")
    }

    companion object {
        private const val TAG = "GlobeNews"

        // Global grid cache: 15 minutes
        private const val GLOBAL_GRID_CACHE_MS = 15 * 60 * 1000L

        // GDELT batching: 5 concurrent, 500ms between batches
        private const val GDELT_BATCH_SIZE = 5
        private const val GDELT_BATCH_DELAY_MS = 500L
        private const val GDELT_GRID_MAX_RECORDS = 75
        private const val GDELT_SUB_MAX_RECORDS = 100

        // Global grid of query regions for zoomed-out view
        val GLOBAL_GRID = listOf(
            // Africa
            QueryRegion("West Africa", 10.0, -5.0, 1500),
            QueryRegion("East Africa", -2.0, 35.0, 1500),
            QueryRegion("North Africa", 30.0, 15.0, 1500),
            QueryRegion("Southern Africa", -25.0, 28.0, 1500),
            // Americas
            QueryRegion("Eastern US/Canada", 40.0, -80.0, 1500),
            QueryRegion("Western US", 37.0, -120.0, 1500),
            QueryRegion("Mexico/Central America", 20.0, -100.0, 1500),
            QueryRegion("Brazil", -15.0, -50.0, 2000),
            QueryRegion("Southern South America", -35.0, -65.0, 1500),
            QueryRegion("Northern South America", 5.0, -70.0, 1500),
            // Europe
            QueryRegion("Western Europe", 48.0, 3.0, 1200),
            QueryRegion("Eastern Europe", 50.0, 25.0, 1500),
            QueryRegion("Scandinavia/Baltics", 60.0, 20.0, 1200),
            QueryRegion("Mediterranean", 40.0, 15.0, 1200),
            QueryRegion("UK/Ireland", 54.0, -2.0, 800),
            // Middle East & Central Asia
            QueryRegion("Middle East", 30.0, 42.0, 1500),
            QueryRegion("Gulf States", 24.0, 52.0, 1000),
            QueryRegion("Central Asia", 42.0, 65.0, 1500),
            // Asia
            QueryRegion("South Asia", 22.0, 78.0, 1500),
            QueryRegion("Southeast Asia", 10.0, 105.0, 1500),
            QueryRegion("East China", 32.0, 118.0, 1500),
            QueryRegion("Japan/Korea", 36.0, 135.0, 1200),
            QueryRegion("Indonesia/Philippines", -2.0, 120.0, 1500),
            // Oceania
            QueryRegion("Australia", -28.0, 135.0, 2000),
            QueryRegion("New Zealand/Pacific", -38.0, 175.0, 1500),
            // Russia
            QueryRegion("Western Russia", 56.0, 40.0, 1500),
            QueryRegion("Siberia/Far East", 55.0, 90.0, 2500),
        )

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

data class QueryRegion(val name: String, val lat: Double, val lon: Double, val radiusKm: Int)

interface ApiKeyProvider {
    val gNewsKey: String
    val newsApiKey: String
    val mediaStackKey: String
    val cesiumIonToken: String
}
