package com.walkietalkie.dictationime.settings

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class CreditStoreActivity : AppCompatActivity() {
    private var selectedOption: View? = null
    private lateinit var buyButton: Button
    private lateinit var currencyValue: TextView
    private val httpClient = OkHttpClient()
    private var selectedCurrency = "USD"
    private val packPrices = mutableMapOf<String, String>()
    private val packByOptionId = mapOf(
        R.id.option149 to "pack_150",
        R.id.option290 to "pack_300",
        R.id.option439 to "pack_500",
        R.id.option1000 to "pack_1000",
        R.id.option2500 to "pack_2500"
    )
    private val fallbackPriceByOptionId = mapOf(
        R.id.option149 to R.string.credit_price_150,
        R.id.option290 to R.string.credit_price_300,
        R.id.option439 to R.string.credit_price_500,
        R.id.option1000 to R.string.credit_price_1000,
        R.id.option2500 to R.string.credit_price_2500
    )
    private val priceViewByPackId = mapOf(
        "pack_150" to R.id.price150,
        "pack_300" to R.id.price300,
        "pack_500" to R.id.price500,
        "pack_1000" to R.id.price1000,
        "pack_2500" to R.id.price2500
    )
    private val supportedCurrencies = listOf("USD", "ZAR", "EUR", "GBP")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit_store)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        buyButton = findViewById(R.id.confirmPurchaseButton)
        currencyValue = findViewById(R.id.currencyValue)
        currencyValue.text = selectedCurrency
        findViewById<View>(R.id.currencyRow).setOnClickListener { showCurrencyPicker() }

        val option149 = findViewById<View>(R.id.option149)
        val option290 = findViewById<View>(R.id.option290)
        val option439 = findViewById<View>(R.id.option439)
        val option1000 = findViewById<View>(R.id.option1000)
        val option2500 = findViewById<View>(R.id.option2500)

        val options = listOf(option149, option290, option439, option1000, option2500)
        options.forEach { view ->
            view.setOnClickListener { selectOption(view) }
        }

        selectOption(option1000)
        loadPricing()

        buyButton.setOnClickListener {
            val selectedId = selectedOption?.id
            val packId = selectedId?.let { packByOptionId[it] }
            if (packId == null) {
                Toast.makeText(this, "Select a pack", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            if (baseUrl.isBlank()) {
                Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val url = "$baseUrl/payfast/checkout?pack=$packId&currency=$selectedCurrency"
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun selectOption(view: View) {
        selectedOption?.setBackgroundResource(R.drawable.bg_credit_option)
        view.setBackgroundResource(R.drawable.bg_credit_option_selected)
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
        AlertDialog.Builder(this)
            .setTitle(R.string.currency_select_title)
            .setItems(supportedCurrencies.toTypedArray()) { _, index ->
                val selected = supportedCurrencies[index]
                if (selected != selectedCurrency) {
                    selectedCurrency = selected
                    currencyValue.text = selected
                    loadPricing()
                }
            }
            .show()
    }

    private fun loadPricing() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, "Backend URL not configured", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "$baseUrl/payfast/packs?currency=$selectedCurrency"
        lifecycleScope.launch {
            try {
                val request = Request.Builder().url(url).build()
                val body = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Pricing fetch failed: ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                }
                val json = JSONObject(body)
                val packs = json.getJSONArray("packs")
                packPrices.clear()

                for (i in 0 until packs.length()) {
                    val pack = packs.getJSONObject(i)
                    val id = pack.getString("id")
                    val amount = pack.getString("amount")
                    val currency = pack.getString("currency")
                    val formatted = formatPrice(amount, currency)
                    packPrices[id] = formatted
                    val priceViewId = priceViewByPackId[id]
                    if (priceViewId != null) {
                        findViewById<TextView>(priceViewId).text = formatted
                    }
                }

                updateBuyButton()
            } catch (e: Exception) {
                Toast.makeText(this@CreditStoreActivity, R.string.currency_load_failed, Toast.LENGTH_SHORT).show()
            }
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
