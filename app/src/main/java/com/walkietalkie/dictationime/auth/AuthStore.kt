package com.walkietalkie.dictationime.auth

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object AuthStore {
    private const val PREFS_NAME = "walkie_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_EMAIL = "email"
    private const val KEY_DEVICE_ID = "device_id"
    private const val EXPIRY_SKEW_SECONDS = 30L

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAccessToken(context: Context): String? {
        return prefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getEmail(context: Context): String? {
        return prefs(context).getString(KEY_EMAIL, null)
    }

    fun getExpiresAt(context: Context): Long {
        return prefs(context).getLong(KEY_EXPIRES_AT, 0L)
    }

    fun isSignedIn(context: Context): Boolean {
        val token = getAccessToken(context)
        if (token.isNullOrBlank()) return false
        val expiresAt = getExpiresAt(context)
        val now = System.currentTimeMillis() / 1000
        return expiresAt > now + EXPIRY_SKEW_SECONDS
    }

    fun saveSession(
        context: Context,
        accessToken: String,
        expiresAtSeconds: Long,
        email: String
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT, expiresAtSeconds)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_EMAIL)
            .apply()
    }

    fun getOrCreateDeviceId(context: Context): String {
        val existing = prefs(context).getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}
