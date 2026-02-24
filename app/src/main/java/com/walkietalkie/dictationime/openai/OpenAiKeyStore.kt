package com.walkietalkie.dictationime.openai

import android.content.Context

object OpenAiKeyStore {
    private const val PREFS_NAME = "walkie_openai"
    private const val KEY_API_KEY = "runtime_api_key"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context): String {
        return prefs(context).getString(KEY_API_KEY, "")?.trim().orEmpty()
    }

    fun setApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }
}
