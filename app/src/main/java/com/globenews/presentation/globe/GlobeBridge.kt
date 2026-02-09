package com.globenews.presentation.globe

import android.webkit.WebView
import com.globenews.core.common.Constants
import com.globenews.domain.model.NewsStory
import org.json.JSONArray
import org.json.JSONObject

class GlobeBridge(private var webView: WebView?) {

    fun addMarkers(stories: List<NewsStory>, altitudeKm: Double = 20000.0) {
        val maxMarkers = markerLimitForAltitude(altitudeKm)
        val jsonArray = JSONArray()
        stories.take(maxMarkers).forEach { story ->
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

    private fun markerLimitForAltitude(altitudeKm: Double): Int {
        return when {
            altitudeKm > 8_000 -> Constants.MAX_VISIBLE_MARKERS_HIGH_ALT
            altitudeKm > 1_000 -> Constants.MAX_VISIBLE_MARKERS_MID_ALT
            else -> Constants.MAX_VISIBLE_MARKERS_LOW_ALT
        }
    }

    private fun evaluateJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}
