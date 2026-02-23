package com.walkietalkie.dictationime.settings

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object TransactionHistoryDataCache {
    private const val PREFS_NAME = "transaction_history_cache"
    private const val KEY_PURCHASES = "purchases_json"

    data class PurchaseRecord(
        val packId: String,
        val minutes: Int,
        val amount: String,
        val currency: String,
        val providerRef: String,
        val createdAtMs: Long
    )

    private val httpClient = OkHttpClient()

    @Volatile
    private var cachedPurchases: List<PurchaseRecord>? = null

    fun getCachedPurchases(context: Context): List<PurchaseRecord>? {
        cachedPurchases?.let { return it }
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PURCHASES, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        PurchaseRecord(
                            packId = item.optString("packId", ""),
                            minutes = item.optInt("minutes", 0).coerceAtLeast(0),
                            amount = item.optString("amount", "0"),
                            currency = item.optString("currency", "USD"),
                            providerRef = item.optString("providerRef", ""),
                            createdAtMs = item.optLong("createdAt", 0L)
                        )
                    )
                }
            }
        }.getOrNull()?.also {
            cachedPurchases = it
        }
    }

    suspend fun prefetch(context: Context) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return
        fetchAndCachePurchases(context, baseUrl)
    }

    suspend fun fetchAndCachePurchases(context: Context, baseUrl: String): List<PurchaseRecord>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = AuthSessionManager.getValidAccessToken(context) ?: return@runCatching null
                val request = Request.Builder()
                    .url("$baseUrl/credits/purchases")
                    .header("Authorization", "Bearer $token")
                    .build()

                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Purchases fetch failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(body)
                val purchases = json.optJSONArray("purchases") ?: JSONArray()
                val items = buildList {
                    for (i in 0 until purchases.length()) {
                        val item = purchases.getJSONObject(i)
                        add(
                            PurchaseRecord(
                                packId = item.optString("packId", ""),
                                minutes = item.optInt("minutes", 0).coerceAtLeast(0),
                                amount = item.optString("amount", "0"),
                                currency = item.optString("currency", "USD"),
                                providerRef = item.optString("providerRef", ""),
                                createdAtMs = item.optLong("createdAt", 0L)
                            )
                        )
                    }
                }

                cachedPurchases = items
                val serialized = JSONArray().apply {
                    items.forEach {
                        put(
                            JSONObject()
                                .put("packId", it.packId)
                                .put("minutes", it.minutes)
                                .put("amount", it.amount)
                                .put("currency", it.currency)
                                .put("providerRef", it.providerRef)
                                .put("createdAt", it.createdAtMs)
                        )
                    }
                }.toString()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PURCHASES, serialized)
                    .apply()
                items
            }.getOrNull()
        }
    }
}
