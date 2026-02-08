package com.globenews.plugin

data class PluginDetailContent(
    val title: String,
    val body: String,
    val url: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
