package com.globenews.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.globenews.presentation.bookmarks.BookmarksScreen
import com.globenews.presentation.globe.GlobeScreen
import com.globenews.presentation.settings.SettingsScreen

@Composable
fun AppNavigation(cesiumToken: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.GLOBE
    ) {
        composable(NavRoutes.GLOBE) {
            GlobeScreen(
                cesiumToken = cesiumToken,
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToBookmarks = { navController.navigate(NavRoutes.BOOKMARKS) }
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.BOOKMARKS) {
            BookmarksScreen(
                onBack = { navController.popBackStack() },
                onStoryClick = { story ->
                    navController.popBackStack()
                }
            )
        }
    }
}
