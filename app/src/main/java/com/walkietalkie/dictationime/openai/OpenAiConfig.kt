package com.walkietalkie.dictationime.openai

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.config.AppModeConfig

object OpenAiConfig {
    val useOpenAiDirect: Boolean
        get() = AppModeConfig.isOpenSourceMode

    fun apiKey(context: Context? = null): String {
        if (!useOpenAiDirect) return ""
        val runtimeKey = context?.let { OpenAiKeyStore.getApiKey(it) }.orEmpty()
        if (runtimeKey.isNotBlank()) return runtimeKey
        // Never ship a bundled key; OSS mode must rely on runtime-provided keys only.
        return ""
    }

    val baseUrl: String
        get() = if (useOpenAiDirect) BuildConfig.OPENAI_BASE_URL else BuildConfig.BACKEND_BASE_URL

    fun isConfigured(context: Context? = null): Boolean {
        if (baseUrl.isBlank()) return false
        return if (useOpenAiDirect) apiKey(context).isNotBlank() else true
    }
}
