package com.walkietalkie.dictationime.auth

import android.util.Log
import com.walkietalkie.dictationime.BuildConfig
import org.json.JSONObject

object AuthApiLogger {
    private const val TAG = "AuthApi"

    fun parseBackendMessage(statusCode: Int, rawBody: String, fallback: String): String {
        if (statusCode >= 500) return fallback
        if (rawBody.isBlank()) return fallback
        return try {
            val json = JSONObject(rawBody)
            when {
                json.has("message") -> json.optString("message", fallback)
                json.has("error") -> {
                    val error = json.opt("error")
                    if (error is JSONObject) error.optString("message", fallback) else fallback
                }
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun logHttpFailure(endpoint: String, code: Int, body: String) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, "HTTP $code from $endpoint body=$body")
    }

    fun logException(endpoint: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, "Request failed for $endpoint", throwable)
    }
}
