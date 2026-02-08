package com.globenews.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.globenews.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val gNewsKey: String = "",
    val newsApiKey: String = "",
    val mediaStackKey: String = "",
    val gdeltEnabled: Boolean = true,
    val gNewsEnabled: Boolean = true,
    val newsApiEnabled: Boolean = true,
    val rssEnabled: Boolean = true,
    val cacheSize: String = "0 KB"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "globenews_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("globenews_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            newsRepository.getCacheSizeBytes().collect { bytes ->
                _uiState.value = _uiState.value.copy(
                    cacheSize = formatBytes(bytes)
                )
            }
        }
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            gNewsKey = prefs.getString("gnews_key", "") ?: "",
            newsApiKey = prefs.getString("newsapi_key", "") ?: "",
            mediaStackKey = prefs.getString("mediastack_key", "") ?: "",
            gdeltEnabled = prefs.getBoolean("gdelt_enabled", true),
            gNewsEnabled = prefs.getBoolean("gnews_enabled", true),
            newsApiEnabled = prefs.getBoolean("newsapi_enabled", true),
            rssEnabled = prefs.getBoolean("rss_enabled", true)
        )
    }

    fun updateGNewsKey(key: String) {
        _uiState.value = _uiState.value.copy(gNewsKey = key)
        prefs.edit().putString("gnews_key", key).apply()
    }

    fun updateNewsApiKey(key: String) {
        _uiState.value = _uiState.value.copy(newsApiKey = key)
        prefs.edit().putString("newsapi_key", key).apply()
    }

    fun updateMediaStackKey(key: String) {
        _uiState.value = _uiState.value.copy(mediaStackKey = key)
        prefs.edit().putString("mediastack_key", key).apply()
    }

    fun toggleSource(source: String, enabled: Boolean) {
        when (source) {
            "gdelt" -> {
                _uiState.value = _uiState.value.copy(gdeltEnabled = enabled)
                prefs.edit().putBoolean("gdelt_enabled", enabled).apply()
            }
            "gnews" -> {
                _uiState.value = _uiState.value.copy(gNewsEnabled = enabled)
                prefs.edit().putBoolean("gnews_enabled", enabled).apply()
            }
            "newsapi" -> {
                _uiState.value = _uiState.value.copy(newsApiEnabled = enabled)
                prefs.edit().putBoolean("newsapi_enabled", enabled).apply()
            }
            "rss" -> {
                _uiState.value = _uiState.value.copy(rssEnabled = enabled)
                prefs.edit().putBoolean("rss_enabled", enabled).apply()
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            newsRepository.clearCache()
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
