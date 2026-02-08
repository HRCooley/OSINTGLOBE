package com.globenews.domain.usecase

import com.globenews.domain.model.LocationResult
import com.globenews.domain.repository.LocationRepository
import javax.inject.Inject

class SearchLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(query: String): Result<List<LocationResult>> {
        if (query.isBlank()) return Result.success(emptyList())
        return locationRepository.searchLocations(query)
    }
}
