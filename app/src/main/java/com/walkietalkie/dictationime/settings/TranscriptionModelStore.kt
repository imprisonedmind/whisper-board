package com.walkietalkie.dictationime.settings

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthSessionManager
import com.walkietalkie.dictationime.model.TranscriptionModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object TranscriptionModelStore {
    private val client = OkHttpClient()

    fun getCached(context: Context): String {
        return SettingsStore.getSelectedModel(context)
    }

    suspend fun fetchRemote(context: Context): String {
        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")

        val request = Request.Builder()
            .url("$baseUrl/profiles/model")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Model fetch failed: ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val modelId = TranscriptionModels.normalizeModelId(
                    JSONObject(body).optString("modelId", "")
                )
                SettingsStore.setSelectedModel(context, modelId)
                modelId
            }
        }
    }

    suspend fun setSelectedModel(context: Context, modelId: String) {
        val normalized = TranscriptionModels.normalizeModelId(modelId)
        SettingsStore.setSelectedModel(context, normalized)

        val token = AuthSessionManager.getValidAccessToken(context)
            ?: throw IOException("Login required")
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) throw IOException("Backend URL missing")

        val body = JSONObject()
            .put("modelId", normalized)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/profiles/model")
            .header("Authorization", "Bearer $token")
            .put(body)
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Model update failed: ${response.code}")
                }
            }
        }
    }
}
