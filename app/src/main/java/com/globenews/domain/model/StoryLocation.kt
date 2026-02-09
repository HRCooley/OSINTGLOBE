package com.globenews.domain.model

/** Geographic coordinates and place metadata for a news story's location on the map. */
data class StoryLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val countryCode: String?,
    val admin1: String?
)
