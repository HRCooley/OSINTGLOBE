package com.globenews.presentation.globe

import android.webkit.WebView
import com.globenews.domain.model.NewsStory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kotlin-side bridge for communicating with the Leaflet.js map in globe.html.
 *
 * Serializes domain [NewsStory] objects to JSON and calls JavaScript functions
 * via [WebView.evaluateJavascript]. All JS calls are posted to the WebView's
 * handler thread to avoid cross-thread issues.
 *
 * The JavaScript API surface is kept stable so that replacing the map engine
 * (e.g., switching tile providers) only requires changes to globe.html.
 *
 * Marker limit: [MAX_MARKERS] (500) stories are sent per update to keep the
 * WebView responsive. The Leaflet MarkerCluster plugin handles grouping.
 */
class GlobeBridge(private var webView: WebView?) {

    companion object {
        private const val MAX_MARKERS = 500
    }

    /**
     * Sends stories to the map for rendering. The JavaScript addMarkers()
     * function handles incremental diffing — only new markers are added and
     * stale markers are removed, avoiding flicker during panning.
     */
    fun addMarkers(stories: List<NewsStory>) {
        val jsonArray = JSONArray()
        stories.take(MAX_MARKERS).forEach { story ->
            val obj = JSONObject().apply {
                put("id", story.id)
                put("lat", story.location.latitude)
                put("lon", story.location.longitude)
                put("scope", story.scope.name)
                put("title", story.title)
                put("sourceName", story.sources.firstOrNull()?.name ?: "")
                put("publishedAt", story.publishedAt.toEpochMilli())
            }
            jsonArray.put(obj)
        }
        evaluateJs("addMarkers('${jsonArray.toString().replace("'", "\\'")}')")
    }

    fun clearMarkers() {
        evaluateJs("clearMarkers()")
    }

    fun flyTo(lat: Double, lon: Double, altitudeKm: Double) {
        evaluateJs("flyTo($lat, $lon, $altitudeKm)")
    }

    fun setBaseLayer(provider: String) {
        evaluateJs("setBaseLayer('$provider')")
    }

    fun highlightMarker(id: String) {
        evaluateJs("highlightMarker('$id')")
    }

    fun release() {
        webView = null
    }

    private fun evaluateJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}
