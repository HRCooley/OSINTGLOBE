package com.globenews.domain.usecase

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.repository.NewsRepository
import com.globenews.plugin.GeoBounds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fetches news stories for a map region, selecting editorial scopes based on
 * the camera altitude. This is the key mechanism that makes the app show
 * international headlines when zoomed out and local stories when zoomed in.
 *
 * Altitude thresholds (derived from Leaflet zoom → synthetic altitude in globe.html):
 * - >15,000 km → International only
 * - >5,000 km  → International + National
 * - >500 km    → National + Regional
 * - <500 km    → Regional + Local
 */
class GetStoriesByRegionUseCase @Inject constructor(
    private val newsRepository: NewsRepository
) {
    operator fun invoke(
        bounds: GeoBounds,
        altitudeKm: Double,
        forceRefresh: Boolean = false
    ): Flow<Result<List<NewsStory>>> {
        val scopes = scopesForAltitude(altitudeKm)
        return newsRepository.getStoriesByRegion(bounds, scopes, forceRefresh)
    }

    private fun scopesForAltitude(altitudeKm: Double): Set<EditorialScope> {
        return when {
            altitudeKm > 15_000 -> setOf(EditorialScope.INTERNATIONAL)
            altitudeKm > 5_000 -> setOf(EditorialScope.INTERNATIONAL, EditorialScope.NATIONAL)
            altitudeKm > 500 -> setOf(EditorialScope.NATIONAL, EditorialScope.REGIONAL)
            else -> setOf(EditorialScope.REGIONAL, EditorialScope.LOCAL)
        }
    }
}
