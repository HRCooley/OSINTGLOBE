package com.globenews.domain.usecase

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.repository.NewsRepository
import com.globenews.plugin.GeoBounds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

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
