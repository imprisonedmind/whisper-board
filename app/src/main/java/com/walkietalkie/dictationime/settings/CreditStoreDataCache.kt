package com.walkietalkie.dictationime.settings

import android.content.Context
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object CreditStoreDataCache {
    private const val PREFS_NAME = "credit_store_cache"
    private const val KEY_BALANCE = "balance_minutes"
    private const val KEY_HAS_BALANCE = "has_balance"
    private const val KEY_PRICES_PREFIX = "prices_"

    private val httpClient = OkHttpClient()

    @Volatile
    private var cachedBalanceMinutes: Int? = null
    private val cachedPricesByCurrency = mutableMapOf<String, Map<String, String>>()

    fun getCachedBalanceMinutes(context: Context): Int? {
        cachedBalanceMinutes?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_BALANCE, false)) return null
        val value = prefs.getInt(KEY_BALANCE, 0).coerceAtLeast(0)
        cachedBalanceMinutes = value
        return value
    }

    fun getCachedPrices(context: Context, currency: String): Map<String, String>? {
        val normalized = currency.uppercase()
        cachedPricesByCurrency[normalized]?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("$KEY_PRICES_PREFIX$normalized", null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
            cachedPricesByCurrency[normalized] = map
            map
        }.getOrNull()
    }

    suspend fun prefetch(context: Context, currency: String = "USD") {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return

        coroutineScope {
            val pricingJob = async { fetchAndCachePricing(context, baseUrl, currency) }
            val balanceJob = async { fetchAndCacheBalance(context, baseUrl) }
            pricingJob.await()
            balanceJob.await()
        }
    }

    suspend fun fetchAndCachePricing(context: Context, baseUrl: String, currency: String): Map<String, String>? {
        val normalized = currency.uppercase()
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/payfast/packs?currency=$normalized")
                    .build()

                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Pricing fetch failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(body)
                val packs = json.getJSONArray("packs")
                val map = mutableMapOf<String, String>()
                for (i in 0 until packs.length()) {
                    val pack = packs.getJSONObject(i)
                    val id = pack.getString("id")
                    val amount = pack.getString("amount")
                    val packCurrency = pack.getString("currency")
                    map[id] = formatPrice(amount, packCurrency)
                }

                cachedPricesByCurrency[normalized] = map
                val serialized = JSONObject(map as Map<String, String>).toString()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("$KEY_PRICES_PREFIX$normalized", serialized)
                    .apply()
                map
            }.getOrNull()
        }
    }

    suspend fun fetchAndCacheBalance(context: Context, baseUrl: String): Int? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = AuthSessionManager.getValidAccessToken(context) ?: return@runCatching null
                val request = Request.Builder()
                    .url("$baseUrl/credits/balance")
                    .header("Authorization", "Bearer $token")
                    .build()
                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Balance fetch failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
                val minutes = JSONObject(body).optInt("minutesRemaining", 0).coerceAtLeast(0)
                cachedBalanceMinutes = minutes
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_HAS_BALANCE, true)
                    .putInt(KEY_BALANCE, minutes)
                    .apply()
                minutes
            }.getOrNull()
        }
    }

    private fun formatPrice(amount: String, currency: String): String {
        return when (currency) {
            "USD" -> "\$$amount"
            "EUR" -> "€$amount"
            "GBP" -> "£$amount"
            "ZAR" -> "R $amount"
            else -> "$amount $currency"
        }
    }
}
