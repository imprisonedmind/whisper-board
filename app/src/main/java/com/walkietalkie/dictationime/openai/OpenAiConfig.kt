package com.walkietalkie.dictationime.openai

import com.walkietalkie.dictationime.BuildConfig

object OpenAiConfig {
    val apiKey: String
        get() = BuildConfig.OPENAI_API_KEY

    val baseUrl: String
        get() = BuildConfig.OPENAI_BASE_URL

    fun isConfigured(): Boolean = apiKey.isNotBlank()
}
