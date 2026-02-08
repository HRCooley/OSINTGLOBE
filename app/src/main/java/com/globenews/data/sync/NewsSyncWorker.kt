package com.globenews.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.repository.NewsRepository
import com.globenews.plugin.GeoBounds
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull

@HiltWorker
class NewsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsRepository: NewsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val lat = inputData.getDouble(KEY_LAT, 0.0)
            val lon = inputData.getDouble(KEY_LON, 0.0)
            val radius = inputData.getDouble(KEY_RADIUS, 30.0)

            val bounds = GeoBounds(
                north = (lat + radius).coerceAtMost(90.0),
                south = (lat - radius).coerceAtLeast(-90.0),
                east = (lon + radius).coerceAtMost(180.0),
                west = (lon - radius).coerceAtLeast(-180.0)
            )

            val scopes = setOf(
                EditorialScope.INTERNATIONAL,
                EditorialScope.NATIONAL,
                EditorialScope.REGIONAL
            )

            newsRepository.getStoriesByRegion(bounds, scopes, forceRefresh = true)
                .firstOrNull()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "news_sync"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_RADIUS = "radius"
    }
}
