package com.globenews.domain.model

data class StoryLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String?,
    val countryCode: String?,
    val admin1: String?
)
