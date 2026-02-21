package com.walkietalkie.dictationime.openai

import com.walkietalkie.dictationime.BuildConfig

object OpenAiConfig {
    val useOpenAiDirect: Boolean
        get() = BuildConfig.USE_OPENAI_DIRECT

    val apiKey: String
        get() = if (useOpenAiDirect) BuildConfig.OPENAI_API_KEY else ""

    val baseUrl: String
        get() = if (useOpenAiDirect) BuildConfig.OPENAI_BASE_URL else BuildConfig.BACKEND_BASE_URL

    fun isConfigured(): Boolean {
        if (baseUrl.isBlank()) return false
        return if (useOpenAiDirect) apiKey.isNotBlank() else true
    }
}
