package com.globenews.domain.usecase

import com.globenews.domain.model.LocationResult
import com.globenews.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchLocationUseCaseTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var useCase: SearchLocationUseCase

    @Before
    fun setup() {
        locationRepository = mockk()
        useCase = SearchLocationUseCase(locationRepository)
    }

    @Test
    fun `blank query returns empty list`() = runTest {
        val result = useCase("")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `whitespace query returns empty list`() = runTest {
        val result = useCase("   ")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `valid query delegates to repository`() = runTest {
        val expected = listOf(
            LocationResult("Paris, France", 48.8566, 2.3522, "city")
        )
        coEvery { locationRepository.searchLocations("Paris") } returns Result.success(expected)

        val result = useCase("Paris")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Paris, France", result.getOrNull()?.first()?.displayName)
        coVerify { locationRepository.searchLocations("Paris") }
    }

    @Test
    fun `repository failure propagates`() = runTest {
        coEvery {
            locationRepository.searchLocations("test")
        } returns Result.failure(RuntimeException("Network error"))

        val result = useCase("test")

        assertTrue(result.isFailure)
    }
}
