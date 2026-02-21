package com.walkietalkie.dictationime.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.walkietalkie.dictationime.R

class CreditStoreActivity : AppCompatActivity() {
    private var selectedOption: View? = null
    private lateinit var buyButton: Button
    private val priceByOptionId = mapOf(
        R.id.option149 to R.string.credit_price_150,
        R.id.option290 to R.string.credit_price_300,
        R.id.option439 to R.string.credit_price_500,
        R.id.option1000 to R.string.credit_price_1000,
        R.id.option2500 to R.string.credit_price_2500
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit_store)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        buyButton = findViewById(R.id.confirmPurchaseButton)

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
    }

    private fun selectOption(view: View) {
        selectedOption?.setBackgroundResource(R.drawable.bg_credit_option)
        view.setBackgroundResource(R.drawable.bg_credit_option_selected)
        selectedOption = view

        val priceRes = priceByOptionId[view.id] ?: return
        val price = getString(priceRes)
        buyButton.text = getString(R.string.credit_buy_button_format, price)
    }
}
