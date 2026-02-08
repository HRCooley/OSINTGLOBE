package com.globenews.core.network

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "GlobeNews/2.0 (Android; contact@globenews.app)")
            .build()
        return chain.proceed(request)
    }
}
