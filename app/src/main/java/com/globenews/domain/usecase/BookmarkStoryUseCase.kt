package com.globenews.domain.usecase

import com.globenews.domain.repository.NewsRepository
import javax.inject.Inject

class BookmarkStoryUseCase @Inject constructor(
    private val newsRepository: NewsRepository
) {
    suspend operator fun invoke(storyId: String, bookmarked: Boolean) {
        newsRepository.bookmarkStory(storyId, bookmarked)
    }
}
