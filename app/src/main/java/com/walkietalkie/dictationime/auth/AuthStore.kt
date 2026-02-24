package com.walkietalkie.dictationime.auth

import android.content.Context
import android.content.SharedPreferences
import com.walkietalkie.dictationime.config.AppModeConfig
import com.walkietalkie.dictationime.settings.CreditStoreDataCache
import com.walkietalkie.dictationime.settings.DataDeletionStatusDataCache
import com.walkietalkie.dictationime.settings.TransactionHistoryDataCache
import java.util.UUID

object AuthStore {
    private const val PREFS_NAME = "walkie_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_EMAIL = "email"
    private const val KEY_DEVICE_ID = "device_id"
    private const val EXPIRY_SKEW_SECONDS = 30L
    private val PREFS_TO_CLEAR_ON_LOGOUT = listOf(
        "walkie_auth",
        "walkie_settings",
        "walkie_openai",
        "home_dashboard_cache",
        "credit_store_cache",
        "walkie_data_tracking",
        "walkie_app_profiles",
        "data_deletion_status_cache",
        "transaction_history_cache"
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAccessToken(context: Context): String? {
        return prefs(context).getString(KEY_ACCESS_TOKEN, null)
    }

    fun getEmail(context: Context): String? {
        return prefs(context).getString(KEY_EMAIL, null)
    }

    fun getRefreshToken(context: Context): String? {
        return prefs(context).getString(KEY_REFRESH_TOKEN, null)
    }

    fun getExpiresAt(context: Context): Long {
        return prefs(context).getLong(KEY_EXPIRES_AT, 0L)
    }

    fun isAccessTokenValid(context: Context, skewSeconds: Long = EXPIRY_SKEW_SECONDS): Boolean {
        val token = getAccessToken(context)
        if (token.isNullOrBlank()) return false
        val now = System.currentTimeMillis() / 1000
        return getExpiresAt(context) > now + skewSeconds
    }

    fun isSignedIn(context: Context): Boolean {
        if (!AppModeConfig.isAuthRequired) return true
        if (isAccessTokenValid(context)) return true
        return !getRefreshToken(context).isNullOrBlank()
    }

    fun saveSession(
        context: Context,
        accessToken: String,
        refreshToken: String?,
        expiresAtSeconds: Long,
        email: String
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtSeconds)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clearSession(context: Context) {
        PREFS_TO_CLEAR_ON_LOGOUT.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        CreditStoreDataCache.clearMemoryCache()
        DataDeletionStatusDataCache.clearMemoryCache()
        TransactionHistoryDataCache.clearMemoryCache()
        context.cacheDir?.deleteRecursively()
        context.cacheDir?.mkdirs()
    }

    fun getOrCreateDeviceId(context: Context): String {
        val existing = prefs(context).getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = UUID.randomUUID().toString()
        prefs(context).edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
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
}
