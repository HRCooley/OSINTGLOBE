package com.globenews.core.common

import java.time.Duration
import java.time.Instant

fun Instant.hoursAgo(): Long {
    return Duration.between(this, Instant.now()).toHours()
}

fun String.normalizeUrl(): String {
    return this
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .trimEnd('/')
}

fun jaccardSimilarity(a: String, b: String): Double {
    val trigramsA = a.lowercase().windowed(3).toSet()
    val trigramsB = b.lowercase().windowed(3).toSet()
    if (trigramsA.isEmpty() && trigramsB.isEmpty()) return 1.0
    if (trigramsA.isEmpty() || trigramsB.isEmpty()) return 0.0
    val intersection = trigramsA.intersect(trigramsB).size
    val union = trigramsA.union(trigramsB).size
    return intersection.toDouble() / union.toDouble()
}
