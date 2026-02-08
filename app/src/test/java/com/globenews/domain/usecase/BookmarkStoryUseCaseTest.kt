package com.globenews.domain.usecase

import com.globenews.domain.repository.NewsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BookmarkStoryUseCaseTest {

    private lateinit var newsRepository: NewsRepository
    private lateinit var useCase: BookmarkStoryUseCase

    @Before
    fun setup() {
        newsRepository = mockk()
        useCase = BookmarkStoryUseCase(newsRepository)
    }

    @Test
    fun `bookmark story delegates to repository`() = runTest {
        coEvery { newsRepository.bookmarkStory("story-1", true) } just runs

        useCase("story-1", true)

        coVerify { newsRepository.bookmarkStory("story-1", true) }
    }

    @Test
    fun `unbookmark story delegates to repository`() = runTest {
        coEvery { newsRepository.bookmarkStory("story-1", false) } just runs

        useCase("story-1", false)

        coVerify { newsRepository.bookmarkStory("story-1", false) }
    }
}
