package com.walkietalkie.dictationime.auth

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AuthSessionManager {
    private const val REFRESH_SKEW_SECONDS = 60L
    private val client = OkHttpClient()
    private val refreshLock = Mutex()

    suspend fun getValidAccessToken(context: Context): String? {
        if (AuthStore.isAccessTokenValid(context, REFRESH_SKEW_SECONDS)) {
            return AuthStore.getAccessToken(context)
        }
        val refreshed = refreshSession(context)
        if (!refreshed) return null
        return AuthStore.getAccessToken(context)
    }

    suspend fun refreshSession(context: Context, force: Boolean = false): Boolean {
        return refreshLock.withLock {
            if (!force && AuthStore.isAccessTokenValid(context, REFRESH_SKEW_SECONDS)) {
                return@withLock true
            }

            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            if (baseUrl.isBlank()) return@withLock false

            val refreshToken = AuthStore.getRefreshToken(context)
            if (refreshToken.isNullOrBlank()) return@withLock false

            val currentEmail = AuthStore.getEmail(context).orEmpty()
            val payload = JSONObject()
                .put("refreshToken", refreshToken)
                .put("deviceId", AuthStore.getOrCreateDeviceId(context))
                .toString()

            val request = Request.Builder()
                .url("$baseUrl/auth/refresh")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            return@withLock withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            AuthStore.clearSession(context)
                            return@use false
                        }

                        val json = JSONObject(body)
                        val accessToken = json.getString("accessToken")
                        val nextRefreshToken = json.getString("refreshToken")
                        val expiresAt = json.getLong("expiresAt")
                        val email = json.optString("email", currentEmail).ifBlank { currentEmail }
                        AuthStore.saveSession(
                            context = context,
                            accessToken = accessToken,
                            refreshToken = nextRefreshToken,
                            expiresAtSeconds = expiresAt,
                            email = email
                        )
                        true
                    }
                }.getOrElse {
                    false
                }
            }
        }
    }
}
