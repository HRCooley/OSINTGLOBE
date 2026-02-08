package com.globenews.plugin

data class PluginMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val color: String?,
    val size: Float = 1.0f
)
