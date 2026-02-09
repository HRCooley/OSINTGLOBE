package com.globenews.presentation.globe

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

data class GlobeViewState(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val altitude: Double = 20000.0,
    val north: Double = 90.0,
    val south: Double = -90.0,
    val east: Double = 180.0,
    val west: Double = -180.0
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GlobeWebView(
    modifier: Modifier = Modifier,
    cesiumToken: String,
    onReady: () -> Unit,
    onMarkerTap: (String) -> Unit,
    onCameraMove: (GlobeViewState) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current

    val bridge = remember {
        AndroidBridgeInterface(
            cesiumToken = cesiumToken,
            onReady = onReady,
            onMarkerTap = onMarkerTap,
            onCameraMove = onCameraMove
        )
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                // Hardware acceleration required for WebGL (Cesium)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(0xFF000000.toInt())

                addJavascriptInterface(bridge, "AndroidBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                        msg?.let {
                            Log.d("GlobeWebView", "${it.messageLevel()}: ${it.message()} [${it.sourceId()}:${it.lineNumber()}]")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        Log.e("GlobeWebView", "Error $errorCode: $description ($failingUrl)")
                    }
                }

                loadUrl("file:///android_asset/globe.html")
                onWebViewCreated(this)
            }
        }
    )
}

class AndroidBridgeInterface(
    private val cesiumToken: String,
    private val onReady: () -> Unit,
    private val onMarkerTap: (String) -> Unit,
    private val onCameraMove: (GlobeViewState) -> Unit
) {
    @JavascriptInterface
    fun getCesiumToken(): String = cesiumToken

    @JavascriptInterface
    fun onReady() {
        onReady.invoke()
    }

    @JavascriptInterface
    fun onMarkerTap(storyId: String) {
        onMarkerTap.invoke(storyId)
    }

    @JavascriptInterface
    fun onCameraMove(viewJson: String) {
        try {
            val org = org.json.JSONObject(viewJson)
            val bounds = org.optJSONObject("bounds")
            val state = GlobeViewState(
                lat = org.optDouble("lat", 0.0),
                lon = org.optDouble("lon", 0.0),
                altitude = org.optDouble("altitude", 20000.0),
                north = bounds?.optDouble("north", 90.0) ?: 90.0,
                south = bounds?.optDouble("south", -90.0) ?: -90.0,
                east = bounds?.optDouble("east", 180.0) ?: 180.0,
                west = bounds?.optDouble("west", -180.0) ?: -180.0
            )
            onCameraMove.invoke(state)
        } catch (e: Exception) {
            // ignore parse errors
        }
    }
}
