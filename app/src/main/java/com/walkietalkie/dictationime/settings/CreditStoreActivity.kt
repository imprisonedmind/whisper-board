package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthSessionManager
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import kotlinx.coroutines.launch
import java.net.URLEncoder

class CreditStoreActivity : AppCompatActivity() {
    private data class CurrencyOption(
        val code: String,
        val name: String,
        val flagEmoji: String
    )

    private var selectedOption: View? = null
    private lateinit var buyButton: Button
    private lateinit var currencyFlag: TextView
    private lateinit var currencyCode: TextView
    private lateinit var currencyName: TextView
    private lateinit var totalCreditsValue: TextView
    private lateinit var scrollView: View
    private lateinit var loadingContainer: View
    private var selectedCurrency = "USD"
    private var hasResumedOnce = false
    private var blockingInitialLoad = false
    private var waitingInitialLoads = 0

    private val currencyOptions = listOf(
        CurrencyOption("USD", "United States Dollar", "🇺🇸"),
        CurrencyOption("ZAR", "South African Rand", "🇿🇦"),
        CurrencyOption("EUR", "Euro", "🇪🇺"),
        CurrencyOption("GBP", "British Pound", "🇬🇧")
    )
    private val packPrices = mutableMapOf<String, String>()
    private val packByOptionId = mapOf(
        R.id.option149 to "pack_150",
        R.id.option290 to "pack_300",
        R.id.option439 to "pack_500",
        R.id.option1000 to "pack_1000",
        R.id.option1500 to "pack_1500",
        R.id.option2500 to "pack_2500"
    )
    private val fallbackPriceByOptionId = mapOf(
        R.id.option149 to R.string.credit_price_150,
        R.id.option290 to R.string.credit_price_300,
        R.id.option439 to R.string.credit_price_500,
        R.id.option1000 to R.string.credit_price_1000,
        R.id.option1500 to R.string.credit_price_1500,
        R.id.option2500 to R.string.credit_price_2500
    )
    private val priceViewByPackId = mapOf(
        "pack_150" to R.id.price150,
        "pack_300" to R.id.price300,
        "pack_500" to R.id.price500,
        "pack_1000" to R.id.price1000,
        "pack_1500" to R.id.price1500,
        "pack_2500" to R.id.price2500
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_credit_store)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        buyButton = findViewById(R.id.confirmPurchaseButton)
        currencyFlag = findViewById(R.id.currencyFlag)
        currencyCode = findViewById(R.id.currencyCode)
        currencyName = findViewById(R.id.currencyName)
        totalCreditsValue = findViewById(R.id.totalCreditsValue)
        scrollView = findViewById(R.id.creditStoreScroll)
        loadingContainer = findViewById(R.id.loadingContainer)

        updateCurrencyRow()
        findViewById<View>(R.id.currencyRow).setOnClickListener { showCurrencyPicker() }

        val options = listOf(
            findViewById<View>(R.id.option149),
            findViewById<View>(R.id.option290),
            findViewById<View>(R.id.option439),
            findViewById<View>(R.id.option1000),
            findViewById<View>(R.id.option1500),
            findViewById<View>(R.id.option2500)
        )
        options.forEach { view ->
            view.setOnClickListener { selectOption(view) }
        }
        selectOption(findViewById(R.id.option1000))

        val hasCachedPricing = applyCachedPricing(selectedCurrency)
        val hasCachedBalance = applyCachedBalance()
        blockingInitialLoad = !hasCachedPricing && !hasCachedBalance
        if (blockingInitialLoad) {
            waitingInitialLoads = 2
            showLoadingOnly(true)
        }

        refreshStoreData()

        buyButton.setOnClickListener { openCheckout() }
    }

    override fun onResume() {
        super.onResume()
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        if (!blockingInitialLoad) {
            loadBalance()
        }
    }

    private fun openCheckout() {
        val selectedId = selectedOption?.id
        val packId = selectedId?.let { packByOptionId[it] }
        if (packId == null) {
            Toast.makeText(this, "Select a pack", Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val token = AuthSessionManager.getValidAccessToken(this@CreditStoreActivity)
            if (token.isNullOrBlank()) {
                AuthStore.clearSession(this@CreditStoreActivity)
                startActivity(Intent(this@CreditStoreActivity, LoginEmailActivity::class.java))
                finish()
                return@launch
            }

            val tokenParam = "&access_token=${URLEncoder.encode(token, "UTF-8")}"
            val url = "$baseUrl/payfast/checkout?pack=$packId&currency=$selectedCurrency$tokenParam"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun refreshStoreData() {
        loadPricing()
        loadBalance()
    }

    private fun selectOption(view: View) {
        selectedOption?.setBackgroundResource(R.drawable.bg_credit_option_brand)
        view.setBackgroundResource(R.drawable.bg_credit_option_brand_selected)
        selectedOption = view
        updateBuyButton()
    }

    private fun updateBuyButton() {
        val selectedId = selectedOption?.id ?: return
        val packId = packByOptionId[selectedId] ?: return
        val price = packPrices[packId]
        val resolvedPrice = price ?: fallbackPriceByOptionId[selectedId]?.let { getString(it) }
        if (resolvedPrice != null) {
            buyButton.text = getString(R.string.credit_buy_button_format, resolvedPrice)
        }
    }

    private fun showCurrencyPicker() {
        val adapter = CurrencyAdapter(currencyOptions)
        AlertDialog.Builder(this)
            .setTitle(R.string.currency_select_title)
            .setAdapter(adapter) { _, index ->
                val selected = currencyOptions[index]
                if (selected.code != selectedCurrency) {
                    selectedCurrency = selected.code
                    updateCurrencyRow()
                    applyCachedPricing(selectedCurrency)
                    loadPricing()
                }
            }
            .show()
    }

    private fun updateCurrencyRow() {
        val selected = currencyOptions.firstOrNull { it.code == selectedCurrency } ?: currencyOptions.first()
        currencyFlag.text = selected.flagEmoji
        currencyCode.text = selected.code
        currencyName.text = selected.name
    }

    private fun loadPricing() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show()
            onInitialLoadFinished()
            return
        }

        lifecycleScope.launch {
            try {
                val fetched = CreditStoreDataCache.fetchAndCachePricing(this@CreditStoreActivity, baseUrl, selectedCurrency)
                if (fetched != null) {
                    packPrices.clear()
                    packPrices.putAll(fetched)
                    for ((packId, viewId) in priceViewByPackId) {
                        val value = fetched[packId] ?: continue
                        findViewById<TextView>(viewId).text = value
                    }
                    updateBuyButton()
                }
            } catch (_: Exception) {
                Toast.makeText(this@CreditStoreActivity, R.string.currency_load_failed, Toast.LENGTH_SHORT).show()
            } finally {
                onInitialLoadFinished()
            }
        }
    }

    private fun loadBalance() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            onInitialLoadFinished()
            return
        }

        lifecycleScope.launch {
            try {
                val fetched = CreditStoreDataCache.fetchAndCacheBalance(this@CreditStoreActivity, baseUrl)
                if (fetched == null) {
                    val token = AuthSessionManager.getValidAccessToken(this@CreditStoreActivity)
                    if (token.isNullOrBlank()) {
                        AuthStore.clearSession(this@CreditStoreActivity)
                        startActivity(Intent(this@CreditStoreActivity, LoginEmailActivity::class.java))
                        finish()
                        return@launch
                    }
                    return@launch
                }
                totalCreditsValue.text = fetched.toString()
            } catch (_: Exception) {
                // Keep existing value on failure.
            } finally {
                onInitialLoadFinished()
            }
        }
    }

    private fun applyCachedBalance(): Boolean {
        val cached = CreditStoreDataCache.getCachedBalanceMinutes(this) ?: return false
        totalCreditsValue.text = cached.toString()
        return true
    }

    private fun applyCachedPricing(currency: String): Boolean {
        val cached = CreditStoreDataCache.getCachedPrices(this, currency) ?: return false
        packPrices.clear()
        packPrices.putAll(cached)
        for ((packId, viewId) in priceViewByPackId) {
            val value = cached[packId] ?: continue
            findViewById<TextView>(viewId).text = value
        }
        updateBuyButton()
        return true
    }

    private fun onInitialLoadFinished() {
        if (!blockingInitialLoad) return
        waitingInitialLoads -= 1
        if (waitingInitialLoads <= 0) {
            blockingInitialLoad = false
            showLoadingOnly(false)
        }
    }

    private fun showLoadingOnly(loading: Boolean) {
        loadingContainer.visibility = if (loading) View.VISIBLE else View.GONE
        scrollView.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private inner class CurrencyAdapter(
        private val items: List<CurrencyOption>
    ) : android.widget.BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): CurrencyOption = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_currency_option, parent, false)
            val item = getItem(position)
            view.findViewById<TextView>(R.id.currencyFlag).text = item.flagEmoji
            view.findViewById<TextView>(R.id.currencyCode).text = item.code
            view.findViewById<TextView>(R.id.currencyName).text = item.name
            return view
        }
    }
}
