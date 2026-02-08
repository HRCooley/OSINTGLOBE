package com.globenews.domain.usecase

import com.globenews.domain.model.NewsStory
import com.globenews.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchStoriesUseCase @Inject constructor(
    private val newsRepository: NewsRepository
) {
    operator fun invoke(query: String): Flow<List<NewsStory>> {
        return newsRepository.searchStories(query)
    }
}
