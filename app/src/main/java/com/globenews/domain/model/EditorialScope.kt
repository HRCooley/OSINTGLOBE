package com.globenews.domain.model

/**
 * Editorial relevance scope for a news story. Determines at which map zoom
 * levels the story is visible:
 * - INTERNATIONAL: visible when fully zoomed out (altitude > 15,000 km)
 * - NATIONAL: visible at country level (altitude > 5,000 km)
 * - REGIONAL: visible at state/province level (altitude > 500 km)
 * - LOCAL: visible only when zoomed in close (altitude < 500 km)
 *
 * Also controls cache TTL — international stories stay cached longer (24h)
 * while local stories expire faster (6h) to stay current.
 */
enum class EditorialScope {
    INTERNATIONAL,
    NATIONAL,
    REGIONAL,
    LOCAL
}
