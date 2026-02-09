package com.globenews.core.common

/** App-wide configuration constants. Cache TTLs are in hours, debounce values in milliseconds. */
object Constants {
    const val DATABASE_NAME = "globenews_db"
    const val FALLBACK_NEWS_FILE = "fallback_news.json"
    const val RSS_FEEDS_FILE = "rss_feeds.json"

    const val GDELT_BASE_URL = "https://api.gdeltproject.org/api/v2/"
    const val NEWSAPI_BASE_URL = "https://newsapi.org/v2/"
    const val GNEWS_BASE_URL = "https://gnews.io/api/v4/"
    const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"

    const val CAMERA_DEBOUNCE_MS = 400L
    const val SEARCH_DEBOUNCE_MS = 300L

    const val MAX_VISIBLE_MARKERS = 500

    const val CACHE_TTL_INTERNATIONAL_HOURS = 24L
    const val CACHE_TTL_NATIONAL_HOURS = 12L
    const val CACHE_TTL_REGIONAL_HOURS = 6L
    const val CACHE_TTL_LOCAL_HOURS = 6L

    const val SYNC_INTERVAL_FOREGROUND_MINUTES = 30L
    const val SYNC_INTERVAL_BACKGROUND_MINUTES = 120L
}
