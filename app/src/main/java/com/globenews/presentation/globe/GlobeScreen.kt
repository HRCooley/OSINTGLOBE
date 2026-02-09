package com.globenews.presentation.globe

import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.globenews.domain.model.EditorialScope
import com.globenews.domain.model.NewsCategory
import com.globenews.domain.model.NewsStory
import com.globenews.presentation.search.SearchBar
import com.globenews.presentation.storydetail.StoryDetailSheet

/**
 * Main screen of the app — a full-screen interactive map with overlaid controls.
 *
 * Layout structure (layered in a Box):
 * 1. [GlobeWebView] — Leaflet.js map filling the entire screen
 * 2. Floating top bar — search bar + settings/bookmarks icons
 * 3. Loading indicator — shown while fetching stories
 * 4. Category chips — horizontally scrollable row for news category filtering
 * 5. Bottom-left — editorial scope filter chips (Intl/Natl/Regional/Local)
 * 6. Bottom-right — tile layer toggle (Dark/Satellite/Street)
 * 7. [ModalBottomSheet] — story detail sheet (shown when a marker is tapped)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(
    viewModel: GlobeViewModel = hiltViewModel(),
    cesiumToken: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToBookmarks: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    val globeBridge = remember { mutableStateOf<GlobeBridge?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Update markers when stories change (incremental - JS handles diff)
    LaunchedEffect(uiState.stories, uiState.globeReady) {
        if (uiState.globeReady) {
            globeBridge.value?.addMarkers(uiState.stories)
        }
    }

    // Update base layer
    LaunchedEffect(uiState.baseLayer) {
        if (uiState.globeReady) {
            globeBridge.value?.setBaseLayer(uiState.baseLayer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Globe fills entire screen
        GlobeWebView(
            modifier = Modifier.fillMaxSize(),
            cesiumToken = cesiumToken,
            onReady = { viewModel.onGlobeReady() },
            onMarkerTap = { storyId -> viewModel.onMarkerTapped(storyId) },
            onCameraMove = { viewState -> viewModel.onCameraMove(viewState) },
            onWebViewCreated = { wv ->
                webView = wv
                globeBridge.value = GlobeBridge(wv)
            }
        )

        // Floating top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onClear = { viewModel.clearSearch() },
                    searchResults = uiState.searchResults,
                    storyResults = uiState.storySearchResults,
                    isSearching = uiState.isSearching,
                    onLocationSelected = { location ->
                        val alt = 500.0
                        globeBridge.value?.flyTo(location.latitude, location.longitude, alt)
                        viewModel.clearSearch()
                    },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                }

                IconButton(
                    onClick = onNavigateToBookmarks,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Bookmark, "Bookmarks", tint = Color.White)
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }

        // Story count badge (top-right, below top bar)
        StoryCountBadge(
            stories = uiState.stories,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 96.dp, end = 12.dp)
        )

        // Category filter chips (horizontally scrollable)
        CategoryChips(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = { viewModel.setCategory(it) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 68.dp)
        )

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 16.dp)
        ) {
            // Scope filter chips
            ScopeChips(
                selectedScopes = uiState.scopeFilter,
                onScopeToggle = { scope ->
                    val current = uiState.scopeFilter.toMutableSet()
                    if (scope in current && current.size > 1) {
                        current.remove(scope)
                    } else {
                        current.add(scope)
                    }
                    viewModel.setScopeFilter(current)
                }
            )
        }

        // Layer toggle - bottom right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 16.dp)
        ) {
            LayerToggle(
                currentLayer = uiState.baseLayer,
                onLayerChange = { layer ->
                    viewModel.setBaseLayer(layer)
                }
            )
        }

        // Story detail bottom sheet
        if (uiState.selectedStory != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissStoryDetail() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                StoryDetailSheet(
                    story = uiState.selectedStory!!,
                    onBookmarkToggle = { viewModel.toggleBookmark(it) },
                    onDismiss = { viewModel.dismissStoryDetail() }
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(
    selectedCategory: NewsCategory,
    onCategorySelected: (NewsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        NewsCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selectedCategory,
                onClick = { onCategorySelected(category) },
                label = { Text(category.displayName, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    selectedLabelColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

@Composable
private fun ScopeChips(
    selectedScopes: Set<EditorialScope>,
    onScopeToggle: (EditorialScope) -> Unit
) {
    val labels = mapOf(
        EditorialScope.INTERNATIONAL to "Intl",
        EditorialScope.NATIONAL to "Natl",
        EditorialScope.REGIONAL to "Regional",
        EditorialScope.LOCAL to "Local"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        labels.forEach { (scope, label) ->
            FilterChip(
                selected = scope in selectedScopes,
                onClick = { onScopeToggle(scope) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    selectedLabelColor = Color.White,
                    labelColor = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(0.dp)
            )
        }
    }
}

@Composable
private fun LayerToggle(
    currentLayer: String,
    onLayerChange: (String) -> Unit
) {
    val layers = listOf("dark" to "Dark", "satellite" to "Sat", "streets" to "Street")

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(6.dp)
    ) {
        layers.forEach { (id, label) ->
            Text(
                text = label,
                color = if (currentLayer == id) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clickable { onLayerChange(id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryCountBadge(
    stories: List<NewsStory>,
    modifier: Modifier = Modifier
) {
    var showBreakdown by remember { mutableStateOf(false) }
    val count = stories.size

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .combinedClickable(
                    onClick = { },
                    onLongClick = { showBreakdown = !showBreakdown }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (count > 0) {
                Text(
                    text = "$count stories in view",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    text = "No stories found",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Source breakdown popup on long-press
        if (showBreakdown && count > 0) {
            val breakdown = stories
                .flatMap { it.sources }
                .groupingBy { it.providerApi }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }

            val providerLabels = mapOf(
                "gdelt" to "GDELT",
                "gnews" to "GNews",
                "newsapi" to "NewsAPI",
                "rss" to "RSS Feeds",
                "google_rss" to "Google News"
            )

            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Source breakdown",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                breakdown.forEach { (provider, provCount) ->
                    Row {
                        Text(
                            text = providerLabels[provider] ?: provider,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$provCount",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
