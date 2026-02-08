package com.globenews.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow

interface PluginContract {
    val id: String
    val displayName: String
    val icon: ImageVector
    fun getMarkers(bounds: GeoBounds, zoom: Double): Flow<List<PluginMarker>>
    fun onMarkerTap(markerId: String): PluginDetailContent?
    fun getSettingsComposable(): (@Composable () -> Unit)?
}
