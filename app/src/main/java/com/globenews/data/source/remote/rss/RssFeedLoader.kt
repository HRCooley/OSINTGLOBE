package com.globenews.data.source.remote.rss

import android.content.Context
import com.globenews.core.common.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFeedLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun loadFeedConfigs(): List<RssFeedConfig> {
        return try {
            val json = context.assets.open(Constants.RSS_FEEDS_FILE)
                .bufferedReader()
                .use { it.readText() }

            val listType = Types.newParameterizedType(List::class.java, RssFeedConfig::class.java)
            val adapter = moshi.adapter<List<RssFeedConfig>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
