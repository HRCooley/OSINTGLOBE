package com.globenews.data.source.remote.googlenews

import com.globenews.data.source.remote.rss.RssFeedConfig
import com.globenews.data.source.remote.rss.RssParser
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleNewsLocale(val hl: String, val gl: String, val ceid: String)

/**
 * Fetches local news via Google News RSS search. Only triggered when
 * zoomed in (altitude < 1000 km). Results are cached per location name
 * for 30 minutes to avoid excessive requests.
 */
@Singleton
class GoogleNewsRssSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val rssParser = RssParser()

    // In-memory cache: locationName -> (timestamp, stories)
    private val cache = ConcurrentHashMap<String, Pair<Long, List<NewsStory>>>()
    private var lastQueriedLocation = ""

    companion object {
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        private const val BASE_URL = "https://news.google.com/rss/search"

        val COUNTRY_LOCALES = mapOf(
            "us" to GoogleNewsLocale("en", "US", "US:en"),
            "gb" to GoogleNewsLocale("en", "GB", "GB:en"),
            "fr" to GoogleNewsLocale("fr", "FR", "FR:fr"),
            "de" to GoogleNewsLocale("de", "DE", "DE:de"),
            "jp" to GoogleNewsLocale("ja", "JP", "JP:ja"),
            "br" to GoogleNewsLocale("pt-BR", "BR", "BR:pt-419"),
            "in" to GoogleNewsLocale("en", "IN", "IN:en"),
            "mx" to GoogleNewsLocale("es", "MX", "MX:es-419"),
            "ng" to GoogleNewsLocale("en", "NG", "NG:en"),
            "eg" to GoogleNewsLocale("ar", "EG", "EG:ar"),
            "kr" to GoogleNewsLocale("ko", "KR", "KR:ko"),
            "ru" to GoogleNewsLocale("ru", "RU", "RU:ru"),
            "au" to GoogleNewsLocale("en", "AU", "AU:en"),
            "za" to GoogleNewsLocale("en", "ZA", "ZA:en"),
            "ke" to GoogleNewsLocale("en", "KE", "KE:en"),
            "ca" to GoogleNewsLocale("en", "CA", "CA:en"),
            "it" to GoogleNewsLocale("it", "IT", "IT:it"),
            "es" to GoogleNewsLocale("es", "ES", "ES:es"),
            "cn" to GoogleNewsLocale("zh-CN", "CN", "CN:zh-Hans"),
            "id" to GoogleNewsLocale("id", "ID", "ID:id"),
            "th" to GoogleNewsLocale("th", "TH", "TH:th"),
            "tr" to GoogleNewsLocale("tr", "TR", "TR:tr"),
            "ar" to GoogleNewsLocale("es", "AR", "AR:es-419"),
            "co" to GoogleNewsLocale("es", "CO", "CO:es-419"),
            "nz" to GoogleNewsLocale("en", "NZ", "NZ:en"),
            "pl" to GoogleNewsLocale("pl", "PL", "PL:pl"),
            "nl" to GoogleNewsLocale("nl", "NL", "NL:nl"),
            "se" to GoogleNewsLocale("sv", "SE", "SE:sv"),
            "no" to GoogleNewsLocale("no", "NO", "NO:no"),
        )
    }

    /**
     * Fetch Google News RSS for a specific location name.
     * Returns cached results if available and fresh. Returns empty if
     * the location hasn't changed since last query (dedup).
     */
    fun fetchForLocation(
        locationName: String,
        countryCode: String?,
        lat: Double,
        lon: Double
    ): List<NewsStory> {
        if (locationName.isBlank()) return emptyList()

        val cacheKey = locationName.lowercase().trim()

        // Return cached results if fresh
        val cached = cache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_DURATION_MS) {
            return cached.second
        }

        // Don't re-query if same location
        if (cacheKey == lastQueriedLocation && cached != null) {
            return cached.second
        }

        return try {
            val locale = COUNTRY_LOCALES[countryCode?.lowercase()] ?: GoogleNewsLocale("en", "US", "US:en")
            val encodedQuery = URLEncoder.encode(locationName, "UTF-8")
            val url = "$BASE_URL?q=$encodedQuery&hl=${locale.hl}&gl=${locale.gl}&ceid=${locale.ceid}"

            android.util.Log.d("GoogleNews", "Fetching: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GlobeNews/2.0 Android")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val xml = response.body?.string() ?: return emptyList()

            // Parse using a temporary RssFeedConfig for the location
            val feedConfig = RssFeedConfig(
                name = "Google News",
                url = url,
                country = countryCode ?: "US",
                language = locale.hl.split("-").first(),
                lat = lat,
                lon = lon,
                scope = "LOCAL"
            )

            val stories = rssParser.parse(xml, feedConfig).map { story ->
                // Override source attribution to identify Google News
                story.copy(
                    sources = listOf(
                        SourceAttribution(
                            name = "Google News",
                            providerApi = "google_rss",
                            retrievedAt = Instant.now()
                        )
                    ),
                    location = StoryLocation(
                        latitude = lat,
                        longitude = lon,
                        placeName = locationName,
                        countryCode = countryCode?.uppercase(),
                        admin1 = null
                    ),
                    scope = EditorialScope.LOCAL
                )
            }

            // Cache results
            cache[cacheKey] = System.currentTimeMillis() to stories
            lastQueriedLocation = cacheKey

            android.util.Log.d("GoogleNews", "Fetched ${stories.size} stories for $locationName")
            stories
        } catch (e: Exception) {
            android.util.Log.d("GoogleNews", "Error: ${e.message}")
            emptyList()
        }
    }
}
