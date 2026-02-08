package com.globenews.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.globenews.domain.model.NewsStory
import com.globenews.domain.usecase.BookmarkStoryUseCase
import com.globenews.domain.usecase.GetBookmarkedStoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val getBookmarkedStories: GetBookmarkedStoriesUseCase,
    private val bookmarkStory: BookmarkStoryUseCase
) : ViewModel() {

    private val _stories = MutableStateFlow<List<NewsStory>>(emptyList())
    val stories: StateFlow<List<NewsStory>> = _stories.asStateFlow()

    init {
        viewModelScope.launch {
            getBookmarkedStories().collect { bookmarks ->
                _stories.value = bookmarks
            }
        }
    }

    fun removeBookmark(storyId: String) {
        viewModelScope.launch {
            bookmarkStory(storyId, false)
        }
    }
}
