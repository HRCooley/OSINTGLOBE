package com.globenews.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.globenews.BuildConfig
import com.globenews.presentation.navigation.AppNavigation
import com.globenews.presentation.theme.GlobeNewsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlobeNewsTheme(darkTheme = true) {
                AppNavigation(cesiumToken = BuildConfig.CESIUM_ION_TOKEN)
            }
        }
    }
}
