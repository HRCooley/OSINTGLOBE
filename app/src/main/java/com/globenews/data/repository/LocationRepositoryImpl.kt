package com.globenews.data.repository

import com.globenews.data.source.geocoding.NominatimService
import com.globenews.domain.model.LocationResult
import com.globenews.domain.repository.LocationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val nominatimService: NominatimService
) : LocationRepository {

    override suspend fun searchLocations(query: String): Result<List<LocationResult>> {
        return try {
            val results = nominatimService.search(query)
            val locationResults = results.map { result ->
                LocationResult(
                    displayName = result.displayName,
                    latitude = result.lat.toDouble(),
                    longitude = result.lon.toDouble(),
                    type = result.type
                )
            }
            Result.success(locationResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
