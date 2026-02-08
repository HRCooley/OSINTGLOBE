package com.globenews.domain.usecase

import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.model.SourceAttribution
import com.globenews.domain.model.StoryLocation
import com.globenews.domain.repository.NewsRepository
import com.globenews.plugin.GeoBounds
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class GetStoriesByRegionUseCaseTest {

    private lateinit var newsRepository: NewsRepository
    private lateinit var useCase: GetStoriesByRegionUseCase

    private val testBounds = GeoBounds(north = 52.0, south = 48.0, east = 15.0, west = 5.0)

    private fun createStory(scope: EditorialScope) = NewsStory(
        id = "test-${scope.name}",
        title = "Test story",
        summary = null,
        url = "https://example.com",
        imageUrl = null,
        publishedAt = Instant.now(),
        location = StoryLocation(50.0, 10.0, "Berlin", "DE", null),
        scope = scope,
        sources = listOf(SourceAttribution("Test", "test", Instant.now())),
        language = "en",
        sentiment = null,
        categories = emptyList()
    )

    @Before
    fun setup() {
        newsRepository = mockk()
        useCase = GetStoriesByRegionUseCase(newsRepository)
    }

    @Test
    fun `high altitude fetches INTERNATIONAL scope only`() = runTest {
        val stories = listOf(createStory(EditorialScope.INTERNATIONAL))
        every {
            newsRepository.getStoriesByRegion(
                testBounds,
                setOf(EditorialScope.INTERNATIONAL),
                false
            )
        } returns flowOf(Result.success(stories))

        val results = useCase(testBounds, 20000.0).toList()

        assertTrue(results.isNotEmpty())
        assertEquals(1, results.first().getOrNull()?.size)
        verify {
            newsRepository.getStoriesByRegion(
                testBounds,
                setOf(EditorialScope.INTERNATIONAL),
                false
            )
        }
    }

    @Test
    fun `mid altitude fetches INTERNATIONAL and NATIONAL scopes`() = runTest {
        val expectedScopes = setOf(EditorialScope.INTERNATIONAL, EditorialScope.NATIONAL)
        every {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        } returns flowOf(Result.success(emptyList()))

        useCase(testBounds, 8000.0).toList()

        verify {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        }
    }

    @Test
    fun `low altitude fetches NATIONAL and REGIONAL scopes`() = runTest {
        val expectedScopes = setOf(EditorialScope.NATIONAL, EditorialScope.REGIONAL)
        every {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        } returns flowOf(Result.success(emptyList()))

        useCase(testBounds, 2000.0).toList()

        verify {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        }
    }

    @Test
    fun `very low altitude fetches REGIONAL and LOCAL scopes`() = runTest {
        val expectedScopes = setOf(EditorialScope.REGIONAL, EditorialScope.LOCAL)
        every {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        } returns flowOf(Result.success(emptyList()))

        useCase(testBounds, 300.0).toList()

        verify {
            newsRepository.getStoriesByRegion(testBounds, expectedScopes, false)
        }
    }
}
