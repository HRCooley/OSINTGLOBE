package com.globenews.domain.usecase

import com.globenews.domain.model.NewsStory
import com.globenews.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarkedStoriesUseCase @Inject constructor(
    private val newsRepository: NewsRepository
) {
    operator fun invoke(): Flow<List<NewsStory>> {
        return newsRepository.getBookmarkedStories()
    }
}
