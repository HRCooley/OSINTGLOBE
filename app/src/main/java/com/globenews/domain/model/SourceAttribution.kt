package com.globenews.domain.model

import java.time.Instant

/** Tracks which news provider contributed a story. A story may have multiple sources
 *  after deduplication merges coverage from different outlets. */
data class SourceAttribution(
    val name: String,
    val providerApi: String,
    val retrievedAt: Instant
)
