package com.walkietalkie.dictationime.settings

import android.content.Context
import android.content.SharedPreferences
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import com.walkietalkie.dictationime.model.TranscriptionModels

object SettingsStore {
    private const val PREFS_NAME = "walkie_settings"
    private const val KEY_OUT_OF_CREDITS_MODE = "out_of_credits_mode"
    private const val KEY_OPEN_SOURCE_MODE = "open_source_mode"
    private const val KEY_SELECTED_MODEL = "selected_transcription_model"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOutOfCreditsMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_OUT_OF_CREDITS_MODE, false)
    }

    fun setOutOfCreditsMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OUT_OF_CREDITS_MODE, enabled).apply()
    }

    fun isOpenSourceMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_OPEN_SOURCE_MODE, false)
    }

    fun setOpenSourceMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPEN_SOURCE_MODE, enabled).apply()
    }

    fun getSelectedModel(context: Context): String {
        val saved = prefs(context).getString(KEY_SELECTED_MODEL, DEFAULT_MODEL_ID)
        return TranscriptionModels.normalizeModelId(saved)
    }

    fun setSelectedModel(context: Context, modelId: String) {
        val normalized = TranscriptionModels.normalizeModelId(modelId)
        prefs(context).edit().putString(KEY_SELECTED_MODEL, normalized).apply()
    }

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isOutOfCreditsKey(key: String?): Boolean {
        return key == KEY_OUT_OF_CREDITS_MODE
    }

    fun isOpenSourceKey(key: String?): Boolean {
        return key == KEY_OPEN_SOURCE_MODE
    }
}
