# OSINTGLOBE (GlobeNews)

A native Android application that aggregates open-source intelligence (OSINT) news from multiple providers and visualizes stories geographically on an interactive 2D world map. Users can pan, zoom, and explore news by region — the app automatically adjusts the editorial scope (international, national, regional, local) based on the current map view.

## Features

- **Interactive 2D Map** — Leaflet.js-powered map with dark, satellite (Esri World Imagery), and street (OpenStreetMap) tile layers
- **Multi-Source News Aggregation** — Concurrent fetching from GDELT, GNews, NewsAPI, and configurable RSS feeds
- **Geographic Filtering** — Stories are fetched based on the visible map region with automatic country-code detection for 45+ countries
- **Altitude-Aware Scoping** — Zooming out surfaces international headlines; zooming in reveals local stories
- **Marker Clustering** — MarkerCluster groups nearby stories into tappable clusters that show scrollable article lists
- **Story Detail Sheet** — Bottom sheet with headline, summary, source attribution, and external link
- **Bookmarks** — Save and manage stories for later reading
- **Location & Story Search** — Unified search bar with Nominatim geocoding and full-text story search
- **Scope Filter Chips** — Toggle visibility of International, National, Regional, and Local stories
- **Background Sync** — WorkManager-based periodic refresh (30 min foreground, 2 hr background)
- **Offline Fallback** — Embedded JSON dataset when all remote sources fail
- **Smart Caching** — Room database with scope-based TTLs (6–24 hours) and automatic expiration
- **Deduplication** — URL normalization + Jaccard title similarity (85% threshold) to merge duplicate stories across sources

## Architecture

The project follows **Clean Architecture** with an MVVM presentation pattern:

```
com.globenews/
├── app/              # Application entry point (Hilt-enabled)
├── presentation/     # Compose UI, ViewModels, WebView bridge
│   ├── globe/        # Map screen, WebView wrapper, JS bridge
│   ├── bookmarks/    # Saved stories screen
│   ├── settings/     # App settings screen
│   ├── search/       # Search bar component
│   ├── storydetail/  # Story detail bottom sheet
│   ├── navigation/   # NavHost + route definitions
│   └── theme/        # Material 3 theming
├── domain/           # Business logic (pure Kotlin, no Android deps)
│   ├── model/        # NewsStory, StoryLocation, EditorialScope, etc.
│   ├── repository/   # Repository interfaces
│   └── usecase/      # GetStoriesByRegion, SearchStories, Bookmarks, etc.
├── data/             # Implementations + data sources
│   ├── repository/   # NewsRepositoryImpl (multi-source aggregation)
│   ├── source/       # Remote (GDELT, GNews, NewsAPI, RSS) + local
│   ├── mapper/       # DTO-to-domain mappers
│   └── sync/         # WorkManager background sync
├── core/             # Cross-cutting concerns
│   ├── database/     # Room DB, DAOs, entities
│   ├── network/      # OkHttp interceptors, connectivity monitor
│   └── common/       # Constants, extension functions
├── di/               # Hilt dependency injection modules
└── plugin/           # Plugin contract for future extensibility
```

### Map Rendering

The map is rendered using **Leaflet.js 1.9.4** inside an Android `WebView`, wrapped in Compose's `AndroidView`. Communication between Kotlin and JavaScript goes through:

- **`GlobeBridge.kt`** — Kotlin-side bridge that serializes story data to JSON and calls JavaScript functions (`addMarkers`, `flyTo`, `setBaseLayer`, etc.)
- **`AndroidBridgeInterface`** — `@JavascriptInterface`-annotated class exposed to JS as `AndroidBridge`, handling callbacks for `onReady`, `onMarkerTap`, and `onCameraMove`
- **`globe.html`** — Self-contained HTML/CSS/JS asset with Leaflet map initialization, cluster management, and the article list popup UI

Markers are updated incrementally (diff-based) — the JavaScript layer tracks marker IDs and only adds new / removes stale markers, avoiding flicker during panning.

### Data Flow

1. User pans/zooms the map → Leaflet fires `moveend` → `AndroidBridge.onCameraMove()` with view bounds + altitude
2. `GlobeViewModel` receives the view state → `GetStoriesByRegionUseCase` determines appropriate editorial scopes based on altitude
3. `NewsRepositoryImpl` checks Room cache first, then fetches concurrently from GDELT, GNews, NewsAPI, and RSS
4. Stories are deduplicated, cached with scope-based TTLs, and emitted as a `Flow<Result<List<NewsStory>>>`
5. ViewModel updates `uiState.stories` → `LaunchedEffect` pushes stories to `GlobeBridge.addMarkers()` → Leaflet renders markers

### Altitude-to-Scope Mapping

| Zoom Level (altitude) | Editorial Scopes Shown |
|---|---|
| > 15,000 km | International only |
| 5,000 – 15,000 km | International + National |
| 500 – 5,000 km | National + Regional |
| < 500 km | Regional + Local |

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose + Material 3 |
| Map Engine | Leaflet.js 1.9.4 + MarkerCluster |
| DI | Hilt (Dagger) |
| Networking | Retrofit 2 + OkHttp + Moshi |
| Persistence | Room + DataStore Preferences |
| Background Work | WorkManager |
| Image Loading | Coil 3 |
| Geocoding | Nominatim (OpenStreetMap) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Java Target | 17 |

## Data Sources

| Source | Type | Coverage | Rate Limit |
|---|---|---|---|
| [GDELT Project](https://www.gdeltproject.org/) | Geographic article search | Worldwide, near/within queries | Unlimited (public) |
| [GNews](https://gnews.io/) | Top headlines by country | 45+ countries via country code | 100 req / 24h |
| [NewsAPI](https://newsapi.org/) | Top headlines by country | 45+ countries via country code | 100 req / 24h |
| RSS Feeds | Configurable feed list | Custom per-feed coordinates | N/A |
| Fallback JSON | Embedded asset | Static sample data | N/A |

## Setup

### Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17
- Android SDK Platform 35 + Build Tools 35.0.0

### API Keys

Create a `local.properties` file in the project root (not checked into version control):

```properties
GNEWS_API_KEY=your_gnews_api_key
NEWSAPI_KEY=your_newsapi_key
MEDIASTACK_KEY=your_mediastack_key
CESIUM_ION_TOKEN=unused_legacy_field
```

The app will still function without API keys — GDELT and RSS feeds require no authentication, and the fallback dataset provides offline coverage.

### Build

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Screens

### Globe Screen (Main)
- Full-screen Leaflet map with news markers
- Search bar (top) with location geocoding + story search
- Settings and Bookmarks icon buttons
- Scope filter chips (bottom-left): Intl / Natl / Regional / Local
- Layer toggle (bottom-right): Dark / Satellite / Street
- Tapping a cluster opens a scrollable article list popup
- Tapping a single marker or article item opens the story detail bottom sheet

### Bookmarks Screen
- List of saved stories with swipe-to-remove

### Settings Screen
- App configuration and cache management

## License

This project is proprietary. All rights reserved.
