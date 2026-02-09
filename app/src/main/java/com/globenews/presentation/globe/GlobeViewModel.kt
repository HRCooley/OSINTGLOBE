package com.globenews.presentation.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.LocationResult
import com.globenews.domain.model.NewsStory
import com.globenews.domain.usecase.BookmarkStoryUseCase
import com.globenews.domain.usecase.GetStoriesByRegionUseCase
import com.globenews.domain.usecase.SearchLocationUseCase
import com.globenews.domain.usecase.SearchStoriesUseCase
import com.globenews.plugin.GeoBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the globe screen. Collected by GlobeScreen as a
 * StateFlow and used to drive Compose recomposition and WebView updates.
 */
data class GlobeUiState(
    val stories: List<NewsStory> = emptyList(),
    val selectedStory: NewsStory? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchResults: List<LocationResult> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val storySearchResults: List<NewsStory> = emptyList(),
    val scopeFilter: Set<EditorialScope> = EditorialScope.entries.toSet(),
    val baseLayer: String = "dark",
    val globeReady: Boolean = false,
    val currentView: GlobeViewState = GlobeViewState()
)

/**
 * ViewModel for the globe (map) screen.
 *
 * Responsibilities:
 * - Reacts to map camera movements by fetching stories for the visible region
 * - Manages editorial scope filtering (the use case selects scopes by altitude)
 * - Handles search queries (location geocoding + full-text story search)
 * - Manages story selection, bookmarking, and base layer switching
 *
 * The [fetchJob] is cancelled and restarted on every camera move so that only
 * the latest visible region is fetched. The [searchJob] is debounced by 300ms
 * to avoid excessive API calls during typing.
 */
@HiltViewModel
class GlobeViewModel @Inject constructor(
    private val getStoriesByRegion: GetStoriesByRegionUseCase,
    private val searchLocation: SearchLocationUseCase,
    private val searchStories: SearchStoriesUseCase,
    private val bookmarkStory: BookmarkStoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobeUiState())
    val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var searchJob: Job? = null

    fun onGlobeReady() {
        _uiState.value = _uiState.value.copy(globeReady = true)
        loadStoriesForCurrentView()
    }

    fun onCameraMove(viewState: GlobeViewState) {
        _uiState.value = _uiState.value.copy(currentView = viewState)
        loadStoriesForCurrentView()
    }

    fun onMarkerTapped(storyId: String) {
        val story = _uiState.value.stories.find { it.id == storyId }
        _uiState.value = _uiState.value.copy(selectedStory = story)
    }

    fun dismissStoryDetail() {
        _uiState.value = _uiState.value.copy(selectedStory = null)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                storySearchResults = emptyList(),
                isSearching = false
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)

            val locationResult = searchLocation(query)
            locationResult.onSuccess { locations ->
                _uiState.value = _uiState.value.copy(searchResults = locations)
            }

            searchStories(query)
                .catch { /* ignore */ }
                .collect { stories ->
                    _uiState.value = _uiState.value.copy(
                        storySearchResults = stories,
                        isSearching = false
                    )
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            storySearchResults = emptyList(),
            isSearching = false
        )
    }

    fun setScopeFilter(scopes: Set<EditorialScope>) {
        _uiState.value = _uiState.value.copy(scopeFilter = scopes)
        loadStoriesForCurrentView()
    }

    fun setBaseLayer(layer: String) {
        _uiState.value = _uiState.value.copy(baseLayer = layer)
    }

    fun toggleBookmark(story: NewsStory) {
        viewModelScope.launch {
            bookmarkStory(story.id, !story.isBookmarked)
            val updated = _uiState.value.stories.map {
                if (it.id == story.id) it.copy(isBookmarked = !it.isBookmarked) else it
            }
            val selectedUpdated = _uiState.value.selectedStory?.let {
                if (it.id == story.id) it.copy(isBookmarked = !it.isBookmarked) else it
            }
            _uiState.value = _uiState.value.copy(
                stories = updated,
                selectedStory = selectedUpdated
            )
        }
    }

    private fun loadStoriesForCurrentView() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val view = _uiState.value.currentView
            val bounds = GeoBounds(
                north = view.north,
                south = view.south,
                east = view.east,
                west = view.west
            )

            _uiState.value = _uiState.value.copy(isLoading = true)

            getStoriesByRegion(bounds, view.altitude)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
                .collect { result ->
                    result.onSuccess { stories ->
                        val filtered = if (_uiState.value.scopeFilter.size < EditorialScope.entries.size) {
                            stories.filter { it.scope in _uiState.value.scopeFilter }
                        } else {
                            stories
                        }
                        _uiState.value = _uiState.value.copy(
                            stories = filtered,
                            isLoading = false,
                            error = null
                        )
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message
                        )
                    }
                }
        }
    }
}
