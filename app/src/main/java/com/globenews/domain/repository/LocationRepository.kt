package com.globenews.domain.repository

import com.globenews.domain.model.LocationResult

interface LocationRepository {
    suspend fun searchLocations(query: String): Result<List<LocationResult>>
}
