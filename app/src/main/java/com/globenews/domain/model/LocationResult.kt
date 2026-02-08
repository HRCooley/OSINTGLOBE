package com.globenews.domain.model

data class LocationResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String?
)
