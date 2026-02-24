package com.walkietalkie.dictationime.settings

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object DataDeletionStatusDataCache {
    private const val PREFS_NAME = "data_deletion_status_cache"
    private const val KEY_REQUESTED_AT = "requested_at"
    private const val KEY_SCHEDULED_AT = "scheduled_at"
    private const val MISSING_TIMESTAMP = Long.MIN_VALUE

    data class DeletionStatus(
        val requestedAt: Long?,
        val scheduledAt: Long?
    )

    private val httpClient = OkHttpClient()

    @Volatile
    private var cachedStatus: DeletionStatus? = null

    fun clearMemoryCache() {
        cachedStatus = null
    }

    fun getCachedStatus(context: Context): DeletionStatus? {
        cachedStatus?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestedAt = prefs.getLong(KEY_REQUESTED_AT, MISSING_TIMESTAMP).takeIf { it != MISSING_TIMESTAMP }
        val scheduledAt = prefs.getLong(KEY_SCHEDULED_AT, MISSING_TIMESTAMP).takeIf { it != MISSING_TIMESTAMP }
        return DeletionStatus(
            requestedAt = requestedAt,
            scheduledAt = scheduledAt
        ).also {
            cachedStatus = it
        }
    }

    fun cacheStatus(context: Context, requestedAt: Long?, scheduledAt: Long?) {
        val value = DeletionStatus(requestedAt = requestedAt, scheduledAt = scheduledAt)
        cachedStatus = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_REQUESTED_AT, requestedAt ?: MISSING_TIMESTAMP)
            .putLong(KEY_SCHEDULED_AT, scheduledAt ?: MISSING_TIMESTAMP)
            .apply()
    }

    suspend fun prefetch(context: Context) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return
        fetchAndCacheStatus(context, baseUrl)
    }

    suspend fun fetchAndCacheStatus(context: Context, baseUrl: String): DeletionStatus? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = AuthSessionManager.getValidAccessToken(context) ?: return@runCatching null
                val request = Request.Builder()
                    .url("$baseUrl/profiles/data-deletion")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Deletion status fetch failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(body)
                DeletionStatus(
                    requestedAt = json.optLongOrNull("requestedAt"),
                    scheduledAt = json.optLongOrNull("scheduledAt")
                ).also {
                    cacheStatus(context, it.requestedAt, it.scheduledAt)
                }
            }.getOrNull()
        }
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key).takeIf { it > 0L }
}
