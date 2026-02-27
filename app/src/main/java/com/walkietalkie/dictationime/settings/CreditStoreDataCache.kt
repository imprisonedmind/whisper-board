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
    data class CreditPackOffer(
        val id: String,
        val priceLabel: String,
        val minutesIncluded: Int,
        val savingsPercent: Int?,
        val savingsAmountLabel: String?
    )

    data class SubscriptionOffer(
        val offerKey: String,
        val checkoutProvider: String?,
        val checkoutSku: String?,
        val title: String,
        val subtitle: String?,
        val priceLabel: String,
        val badge: String?,
        val minutesIncluded: Int?,
        val unlimited: Boolean
    )

    data class PricingCatalog(
        val packsById: Map<String, CreditPackOffer>,
        val subscriptionOffers: List<SubscriptionOffer>
    )

    private const val PREFS_NAME = "credit_store_cache"
    private const val KEY_BALANCE = "balance_minutes"
    private const val KEY_HAS_BALANCE = "has_balance"
    private const val KEY_CATALOG_PREFIX = "pricing_catalog_v2_"

    private val httpClient = OkHttpClient()

    @Volatile
    private var cachedBalanceMinutes: Int? = null
    private val cachedPricingCatalogByCurrency = mutableMapOf<String, PricingCatalog>()

    fun clearMemoryCache() {
        cachedBalanceMinutes = null
        cachedPricingCatalogByCurrency.clear()
    }

    fun getCachedBalanceMinutes(context: Context): Int? {
        cachedBalanceMinutes?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_BALANCE, false)) return null
        val value = prefs.getInt(KEY_BALANCE, 0).coerceAtLeast(0)
        cachedBalanceMinutes = value
        return value
    }

    fun getCachedPricingCatalog(context: Context, currency: String): PricingCatalog? {
        val normalized = currency.uppercase()
        cachedPricingCatalogByCurrency[normalized]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("$KEY_CATALOG_PREFIX$normalized", null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val packs = root.optJSONArray("packs")
            val packMap = mutableMapOf<String, CreditPackOffer>()
            for (i in 0 until (packs?.length() ?: 0)) {
                val item = packs!!.getJSONObject(i)
                val id = item.optString("id", "")
                if (id.isBlank()) continue
                packMap[id] = CreditPackOffer(
                    id = id,
                    priceLabel = item.optString("priceLabel", ""),
                    minutesIncluded = item.optInt("minutesIncluded", 0).coerceAtLeast(0),
                    savingsPercent = if (item.isNull("savingsPercent")) null else item.optInt("savingsPercent"),
                    savingsAmountLabel = item.optString("savingsAmountLabel").ifBlank { null }
                )
            }

            val subscription = root.optJSONObject("subscription")?.let { sub ->
                listOf(
                    SubscriptionOffer(
                        offerKey = sub.optString("offerKey", ""),
                        checkoutProvider = sub.optString("checkoutProvider").ifBlank { null },
                        checkoutSku = sub.optString("checkoutSku").ifBlank { null },
                        title = sub.optString("title", ""),
                        subtitle = sub.optString("subtitle").ifBlank { null },
                        priceLabel = sub.optString("priceLabel", ""),
                        badge = sub.optString("badge").ifBlank { null },
                        minutesIncluded = if (sub.isNull("minutesIncluded")) null else sub.optInt("minutesIncluded"),
                        unlimited = sub.optBoolean("unlimited", false)
                    )
                )
            } ?: run {
                val subscriptionsJson = root.optJSONArray("subscriptions")
                val list = mutableListOf<SubscriptionOffer>()
                for (i in 0 until (subscriptionsJson?.length() ?: 0)) {
                    val sub = subscriptionsJson!!.getJSONObject(i)
                    list.add(
                        SubscriptionOffer(
                            offerKey = sub.optString("offerKey", ""),
                            checkoutProvider = sub.optString("checkoutProvider").ifBlank { null },
                            checkoutSku = sub.optString("checkoutSku").ifBlank { null },
                            title = sub.optString("title", ""),
                            subtitle = sub.optString("subtitle").ifBlank { null },
                            priceLabel = sub.optString("priceLabel", ""),
                            badge = sub.optString("badge").ifBlank { null },
                            minutesIncluded = if (sub.isNull("minutesIncluded")) null else sub.optInt("minutesIncluded"),
                            unlimited = sub.optBoolean("unlimited", false)
                        )
                    )
                }
                list
            }

            PricingCatalog(
                packsById = packMap,
                subscriptionOffers = subscription
            ).also { cachedPricingCatalogByCurrency[normalized] = it }
        }.getOrNull()
    }

    suspend fun prefetch(context: Context, currency: String = "USD") {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return

        coroutineScope {
            val pricingJob = async { fetchAndCachePricingCatalog(context, baseUrl, currency) }
            val balanceJob = async { fetchAndCacheBalance(context, baseUrl) }
            pricingJob.await()
            balanceJob.await()
        }
    }

    suspend fun fetchAndCachePricingCatalog(context: Context, baseUrl: String, currency: String): PricingCatalog? {
        val normalized = currency.uppercase()
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/pricing/catalog?currency=$normalized")
                    .build()

                val body = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Pricing fetch failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
                val json = JSONObject(body)
                val offers = json.optJSONArray("offers")
                val packMap = mutableMapOf<String, CreditPackOffer>()
                var subscriptionOffer: SubscriptionOffer? = null
                val subscriptions = mutableListOf<SubscriptionOffer>()

                for (i in 0 until (offers?.length() ?: 0)) {
                    val offer = offers!!.getJSONObject(i)
                    val kind = offer.optString("kind")
                    val amount = offer.optString("baseAmount", "")
                    val offerCurrency = offer.optString("baseCurrency", "USD")
                    val priceLabel = formatPrice(amount, offerCurrency)

                    if (kind == "credit_pack") {
                        val id = offer.optString("checkoutSku", "")
                        if (id.isBlank()) continue
                        val savingsAmount = offer.optString("savingsAmount").ifBlank { null }
                        packMap[id] = CreditPackOffer(
                            id = id,
                            priceLabel = priceLabel,
                            minutesIncluded = offer.optInt("minutesIncluded", 0).coerceAtLeast(0),
                            savingsPercent = if (offer.isNull("savingsPercent")) null else offer.optInt("savingsPercent"),
                            savingsAmountLabel = savingsAmount?.let { formatPrice(it, offerCurrency) }
                        )
                    } else if (kind == "subscription_unlimited_monthly" || kind == "subscription_monthly") {
                        val current = SubscriptionOffer(
                            offerKey = offer.optString("offerKey", ""),
                            checkoutProvider = offer.optString("checkoutProvider").ifBlank { null },
                            checkoutSku = offer.optString("checkoutSku").ifBlank { null },
                            title = offer.optString("title", "Unlimited Monthly"),
                            subtitle = offer.optString("subtitle").ifBlank { null },
                            priceLabel = priceLabel,
                            badge = offer.optString("badge").ifBlank { null },
                            minutesIncluded = if (offer.isNull("minutesIncluded")) null else offer.optInt("minutesIncluded"),
                            unlimited = offer.optBoolean("unlimited", false)
                        )
                        subscriptions.add(current)
                        if (current.unlimited) {
                            subscriptionOffer = current
                        }
                    }
                }

                val serialized = JSONObject().apply {
                    val packsJson = org.json.JSONArray()
                    for ((_, pack) in packMap) {
                        packsJson.put(JSONObject().apply {
                            put("id", pack.id)
                            put("priceLabel", pack.priceLabel)
                            put("minutesIncluded", pack.minutesIncluded)
                            put("savingsPercent", pack.savingsPercent)
                            put("savingsAmountLabel", pack.savingsAmountLabel)
                        })
                    }
                    put("packs", packsJson)
                    put("subscriptions", org.json.JSONArray().apply {
                        for (sub in subscriptions) {
                            put(JSONObject().apply {
                                put("offerKey", sub.offerKey)
                                put("checkoutProvider", sub.checkoutProvider)
                                put("checkoutSku", sub.checkoutSku)
                                put("title", sub.title)
                                put("subtitle", sub.subtitle)
                                put("priceLabel", sub.priceLabel)
                                put("badge", sub.badge)
                                put("minutesIncluded", sub.minutesIncluded)
                                put("unlimited", sub.unlimited)
                            })
                        }
                    })
                    if (subscriptionOffer != null) {
                        put("subscription", JSONObject().apply {
                            put("offerKey", subscriptionOffer.offerKey)
                            put("checkoutProvider", subscriptionOffer.checkoutProvider)
                            put("checkoutSku", subscriptionOffer.checkoutSku)
                            put("title", subscriptionOffer.title)
                            put("subtitle", subscriptionOffer.subtitle)
                            put("priceLabel", subscriptionOffer.priceLabel)
                            put("badge", subscriptionOffer.badge)
                            put("minutesIncluded", subscriptionOffer.minutesIncluded)
                            put("unlimited", subscriptionOffer.unlimited)
                        })
                    }
                }.toString()

                val catalog = PricingCatalog(
                    packsById = packMap,
                    subscriptionOffers = subscriptions
                )
                cachedPricingCatalogByCurrency[normalized] = catalog
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("$KEY_CATALOG_PREFIX$normalized", serialized)
                    .apply()
                catalog
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
