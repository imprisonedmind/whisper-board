package com.walkietalkie.dictationime.settings

import android.content.Context
import android.content.SharedPreferences
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import com.walkietalkie.dictationime.model.TranscriptionModels

object SettingsStore {
    private const val PREFS_NAME = "walkie_settings"
    private const val KEY_SELECTED_MODEL = "selected_transcription_model"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedModel(context: Context): String {
        val saved = prefs(context).getString(KEY_SELECTED_MODEL, DEFAULT_MODEL_ID)
        return TranscriptionModels.normalizeModelId(saved)
    }

    fun setSelectedModel(context: Context, modelId: String) {
        val normalized = TranscriptionModels.normalizeModelId(modelId)
        prefs(context).edit().putString(KEY_SELECTED_MODEL, normalized).apply()
    }
}
