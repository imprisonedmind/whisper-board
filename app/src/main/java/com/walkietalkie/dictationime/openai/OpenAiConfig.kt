package com.walkietalkie.dictationime.openai

import com.walkietalkie.dictationime.BuildConfig

object OpenAiConfig {
    @Volatile
    private var openSourceOverride: Boolean? = null

    fun setOpenSourceOverride(enabled: Boolean?) {
        openSourceOverride = enabled
    }

    val useOpenAiDirect: Boolean
        get() = openSourceOverride ?: BuildConfig.USE_OPENAI_DIRECT

    val apiKey: String
        get() = if (useOpenAiDirect) BuildConfig.OPENAI_API_KEY else ""

    val baseUrl: String
        get() = if (useOpenAiDirect) BuildConfig.OPENAI_BASE_URL else BuildConfig.BACKEND_BASE_URL

    fun isConfigured(): Boolean {
        if (baseUrl.isBlank()) return false
        return if (useOpenAiDirect) apiKey.isNotBlank() else true
    }
}
