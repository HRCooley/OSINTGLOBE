# OSINTGLOBE — Production Journal

This document chronicles the major technical decisions, challenges, and solutions encountered during the development of OSINTGLOBE (GlobeNews). It serves as a reference for future contributors and as a record of the engineering trade-offs that shaped the product.

---

## Phase 1: Initial Architecture & Cesium Integration

### Goal
Build an Android app that displays OSINT news stories on a 3D interactive globe, allowing users to explore the world and discover news by geographic region.

### Stack Selection
- **Kotlin + Jetpack Compose** — Modern Android UI toolkit with declarative patterns. Compose's state-driven UI simplifies reactive data binding with the ViewModel layer.
- **Clean Architecture** — Domain/data/presentation separation ensures testability and keeps business logic independent of Android framework details.
- **Hilt (Dagger)** — Standard DI for Android. Provides constructor injection across ViewModels, repositories, and services without manual wiring.
- **CesiumJS** — Chosen as the initial 3D globe engine. Loaded inside an Android WebView via `file:///android_asset/globe.html`. CesiumJS offered satellite imagery tiles, 3D terrain, and entity placement out of the box.

### Data Layer Design
- **Multi-source aggregation** — From the start, the repository was designed to fetch from multiple news APIs concurrently using `coroutineScope { async { ... } }`. This minimizes latency since all sources are hit in parallel.
- **Deduplication engine** — Stories from different sources about the same event are merged using URL normalization and Jaccard title similarity (85% threshold within a 1-hour time window).
- **Scope-based TTL caching** — International stories (slower news cycle) cached for 24 hours; local stories (fast-moving) cached for 6 hours. Room database handles persistence with automatic expiration cleanup.

### WebView Bridge Pattern
The Kotlin ↔ JavaScript communication follows a bidirectional bridge:
- **Kotlin → JS**: `GlobeBridge.kt` serializes data to JSON and calls `WebView.evaluateJavascript()` to invoke functions defined in `globe.html`.
- **JS → Kotlin**: `@JavascriptInterface` methods on `AndroidBridgeInterface` are exposed as `window.AndroidBridge` in JavaScript, allowing the map to notify the app of user interactions (marker taps, camera moves).

This pattern decouples the map rendering technology from the app — replacing the map engine only requires updating `globe.html` and adjusting `GlobeBridge.kt`, with no changes to the ViewModel or data layer.

---

## Phase 2: The WebGL Rendering Crisis

### Problem
CesiumJS rendered correctly in desktop browsers and Chrome on Android, but displayed only a solid background color inside the app's WebView. The 3D globe geometry, imagery tiles, and all entities were completely invisible.

### Diagnostic Steps

1. **HTML/CSS verification** — Changed the `<body>` background to a bright color. The color appeared in the WebView, confirming HTML and CSS were loading and rendering correctly.

2. **On-screen debug overlay** — Added a diagnostic panel directly in `globe.html` showing:
   - WebGL context: **YES** (successfully created)
   - Canvas dimensions: **360x800** (correct)
   - Drawing buffer: **360x800** (matches canvas)
   - Viewer created: **OK**
   - Globe visible: **true**
   - Scene mode: **3** (3D)

   Everything reported as working. The WebGL context existed, the canvas was sized correctly, and CesiumJS believed it was rendering.

3. **Pixel readback** — Used `readPixels()` to sample the center of the canvas. Result: `rgba(26, 0, 0, 255)` — exactly the clear color. No globe geometry was being composited into the framebuffer output.

4. **GPU and GL error check** — Added `gl.getError()` polling and GPU identification:
   - GPU: **Adreno (TM) 650**
   - GL errors: **NONE**
   - No shader compilation failures, no missing extensions.

5. **2D mode test** — Switched CesiumJS to `Cesium.SceneMode.SCENE2D` and added a bright test entity. Still only the clear color appeared. Confirmed the issue was not specific to 3D perspective rendering.

### Root Cause
**WebGL draw call compositing bug in Android WebView inside Jetpack Compose's `AndroidView`.**

The clear operation (`gl.clear()`) composites correctly — background color is visible. But geometry draw calls (`gl.drawElements()`, `gl.drawArrays()`) produce output that is not composited into the final surface presented by the `AndroidView`. This appears to be a platform-level issue with how Android's `SurfaceView`/`TextureView` backing for WebGL interacts with Compose's view hierarchy.

Key evidence:
- WebGL context creates successfully
- Shaders compile, no GL errors
- `readPixels()` returns only the clear color (draw calls execute but output is discarded)
- Identical HTML renders correctly in standalone Chrome
- Hardware acceleration is enabled (`android:hardwareAccelerated="true"`)
- Tested on Snapdragon 865 (Adreno 650) — a well-supported GPU

### Decision
After exhausting all WebGL configuration options (`preserveDrawingBuffer`, `logDepthBuffer` disable, `powerPreference`, explicit canvas sizing, `requestAnimationFrame` forcing), the decision was made to **replace CesiumJS with a non-WebGL rendering solution**.

---

## Phase 3: Migration to Leaflet.js

### Why Leaflet
- **DOM/Canvas2D rendering** — Leaflet renders tile layers as `<img>` elements and markers as `<div>` elements. No WebGL dependency. This sidesteps the WebView compositing bug entirely.
- **Lightweight** — ~40 KB gzipped. Loads instantly from CDN in the WebView.
- **Mature ecosystem** — MarkerCluster plugin provides the clustering UX out of the box.
- **Satellite imagery** — Esri World Imagery tiles provide satellite coverage comparable to what CesiumJS offered (minus 3D terrain).

### Trade-offs Accepted
- **No 3D globe** — The app renders a flat 2D Mercator projection instead of a 3D sphere. The user acknowledged this: *"I prefer a globe but I'd be fine with a 2d map if need be. I just want the ability for satellite imagery."*
- **No terrain** — 2D tiles cannot show elevation. Acceptable for a news visualization app.
- **Zoom-to-altitude mapping** — Leaflet's zoom levels (0–20) needed to be mapped to "altitude in km" values to maintain the altitude-based scope filtering from the CesiumJS design. The mapping uses `altitude = 40000 / 2^(zoom-1)`.

### Implementation
The entire `globe.html` was rewritten from CesiumJS to Leaflet.js. The JavaScript API surface (`addMarkers`, `clearMarkers`, `flyTo`, `setBaseLayer`, `highlightMarker`) was kept identical, so `GlobeBridge.kt` required no changes. The migration was transparent to the ViewModel and data layers.

Three tile layers are available:
- **Dark** — CartoDB Dark Matter (default, matches the dark UI theme)
- **Satellite** — Esri World Imagery
- **Streets** — OpenStreetMap

### Result
The map rendered immediately on first load. Tile imagery, markers, and all UI interactions worked without any compositing issues.

---

## Phase 4: Marker UX Refinement

### Problem 1: Spiderfy Unusable on Mobile
MarkerCluster's default "spiderfy" behavior (spreading overlapping markers into a circle connected by lines) was difficult to use on small touchscreens. Markers were too close together and their content wasn't visible until tapped individually.

### Solution
Disabled spiderfy (`spiderfyOnMaxZoom: false`). Replaced it with a **scrollable article list popup** that appears when tapping a cluster:
- Shows up to 20 story headlines sorted by recency
- Each item displays: scope color dot, headline (2-line clamp), source name, and relative time ("3h ago")
- Tapping an article item closes the popup and opens the story detail bottom sheet
- For clusters with 3 or fewer markers at low zoom, the cluster zooms in instead of showing the popup

### Problem 2: Markers Flickering on Pan/Zoom
Every camera move triggered `clearMarkers()` + `addMarkers()` in sequence. During the brief gap, all markers would disappear and reappear, creating a distracting flicker.

### Solution
Implemented **incremental (diff-based) marker updates**:
1. JavaScript maintains a `markers` dictionary keyed by story ID
2. `addMarkers()` receives the full current story set, diffs it against existing markers, and only adds new / removes stale markers
3. Batch operations via `clusterGroup.addLayers()` / `clusterGroup.removeLayers()` for performance
4. Removed the `clearMarkers()` call from the Kotlin `LaunchedEffect` — the JS diff handles everything

---

## Phase 5: Worldwide News Coverage

### Problem
Users noticed that only the Western Hemisphere (primarily the US) had news markers, even when panning to Europe, Asia, or Africa.

### Root Cause
Both GNews and NewsAPI support a `country` parameter for filtering headlines by country, but the app was not passing it. Without a country parameter, these APIs default to US/English headlines regardless of the map's viewing region.

GDELT supports geographic `near:lat,lon within:radius` queries and worked worldwide, but its results alone weren't sufficient for comprehensive coverage.

### Solution
Added a **coordinate-to-country-code lookup** in `NewsRepositoryImpl`:
- 45+ country bounding boxes covering all continents
- The center of the current map view is tested against each bounding box
- The matched 2-letter country code is passed to both GNews (`country=xx`) and NewsAPI (`country=xx`)
- For ocean or unmapped regions, `null` is passed and the APIs return global results

Also increased the RSS feed limit from 10 to 25 feeds per request to improve non-API coverage.

### Countries Covered
North America (US, CA, MX), South America (BR, AR, CO), Europe (GB, FR, DE, IT, ES, PL, HU, UA, BG, RO, CH, AT, BE, NL, NO, SE, RU), Middle East (TR, IL, SA, AE), Africa (EG, NG, ZA, KE, MA), Asia (IN, CN, JP, KR, ID, TH, SG, MY, PH, TW, HK, PK), Oceania (AU, NZ).

---

## Technical Decisions Reference

| Decision | Chosen | Alternative Considered | Rationale |
|---|---|---|---|
| Map engine | Leaflet.js (2D) | CesiumJS (3D) | WebGL compositing bug in Android WebView + Compose |
| Tile provider (dark) | CartoDB Dark Matter | Mapbox Dark | No API key required, good contrast for colored markers |
| Tile provider (satellite) | Esri World Imagery | Google Satellite | Free tier, no API key required |
| Cluster click behavior | Article list popup | Spiderfy | Touch-friendlier on mobile, shows headlines before tapping |
| Marker updates | Incremental diff | Clear + rebuild | Eliminates flicker, better performance |
| Country detection | Bounding box lookup | Reverse geocoding API | Zero-latency, no network call, good enough accuracy |
| News deduplication | URL + Jaccard title | URL only | Catches same-story-different-URL from multiple outlets |
| Cache strategy | Scope-based TTL | Fixed TTL | International news has a longer relevance window than local |

---

## Build Notes

- **Min SDK 26** (Android 8.0) — Required for `java.time.Instant` without desugaring
- **Target SDK 35** — Latest stable Android platform
- **Version**: 2.0.0 — Major version bump reflects the CesiumJS → Leaflet migration
- **Hardware acceleration**: Explicitly enabled in AndroidManifest (`android:hardwareAccelerated="true"`)
- **WebView settings**: JavaScript enabled, DOM storage enabled, mixed content mode ALWAYS_ALLOW (required for CDN-loaded Leaflet assets in a `file://` page)
