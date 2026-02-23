package com.walkietalkie.dictationime.settings

import android.content.Context
import android.content.SharedPreferences

object AppProfilesStore {
    private const val PREFS_NAME = "walkie_app_profiles"
    private const val KEY_DEFAULT_PROMPT = "default_prompt"
    private const val APP_PROMPT_PREFIX = "app_prompt_"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDefaultPrompt(context: Context): String {
        return prefs(context).getString(KEY_DEFAULT_PROMPT, "").orEmpty()
    }

    fun setDefaultPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_DEFAULT_PROMPT, prompt.trim()).apply()
    }

    fun getAppPrompt(context: Context, packageName: String): String {
        return prefs(context).getString(APP_PROMPT_PREFIX + packageName, "").orEmpty()
    }

    fun setAppPrompt(context: Context, packageName: String, prompt: String) {
        val trimmed = prompt.trim()
        val editor = prefs(context).edit()
        if (trimmed.isBlank()) {
            editor.remove(APP_PROMPT_PREFIX + packageName)
        } else {
            editor.putString(APP_PROMPT_PREFIX + packageName, trimmed)
        }
        editor.apply()
    }
}
