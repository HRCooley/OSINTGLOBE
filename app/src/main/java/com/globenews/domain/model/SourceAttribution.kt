package com.globenews.domain.model

import java.time.Instant

data class SourceAttribution(
    val name: String,
    val providerApi: String,
    val retrievedAt: Instant
)
