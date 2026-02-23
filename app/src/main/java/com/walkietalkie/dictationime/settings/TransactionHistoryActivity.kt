package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionHistoryActivity : AppCompatActivity() {
    private lateinit var historyListContainer: LinearLayout
    private lateinit var historyEmptyText: TextView
    private var hasRenderedCache = false

    private data class PurchaseHistoryRow(
        val packId: String,
        val minutes: Int,
        val amount: String,
        val currency: String,
        val providerRef: String,
        val createdAtMs: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_transaction_history)
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        historyListContainer = findViewById(R.id.historyListContainer)
        historyEmptyText = findViewById(R.id.historyEmptyText)

        val cached = TransactionHistoryDataCache.getCachedPurchases(this)
        if (cached != null) {
            hasRenderedCache = true
            renderPurchases(
                cached.map {
                    PurchaseHistoryRow(
                        packId = it.packId,
                        minutes = it.minutes,
                        amount = it.amount,
                        currency = it.currency,
                        providerRef = it.providerRef,
                        createdAtMs = it.createdAtMs
                    )
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadPurchases()
    }

    private fun loadPurchases() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return

        lifecycleScope.launch {
            try {
                if (!AuthStore.isSignedIn(this@TransactionHistoryActivity)) {
                    AuthStore.clearSession(this@TransactionHistoryActivity)
                    startActivity(Intent(this@TransactionHistoryActivity, LoginEmailActivity::class.java))
                    finish()
                    return@launch
                }
                val fetched = TransactionHistoryDataCache.fetchAndCachePurchases(this@TransactionHistoryActivity, baseUrl)
                if (fetched != null) {
                    renderPurchases(
                        fetched.map {
                            PurchaseHistoryRow(
                                packId = it.packId,
                                minutes = it.minutes,
                                amount = it.amount,
                                currency = it.currency,
                                providerRef = it.providerRef,
                                createdAtMs = it.createdAtMs
                            )
                        }
                    )
                } else if (!hasRenderedCache) {
                    Toast.makeText(
                        this@TransactionHistoryActivity,
                        R.string.history_load_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                if (!hasRenderedCache) {
                    Toast.makeText(
                        this@TransactionHistoryActivity,
                        R.string.history_load_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun renderPurchases(items: List<PurchaseHistoryRow>) {
        hasRenderedCache = true
        historyListContainer.removeAllViews()
        if (items.isEmpty()) {
            historyEmptyText.visibility = View.VISIBLE
            return
        }

        historyEmptyText.visibility = View.GONE
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = getDrawable(R.drawable.bg_card)
                setPadding(dp(10), dp(9), dp(10), dp(9))
                isClickable = true
                isFocusable = true
            }

            val detailText = TextView(this).apply {
                setTextColor(getColor(R.color.ink))
                textSize = 13f
                text = if (item.minutes > 0) {
                    "${item.minutes} minutes • ${formatMoney(item.amount, item.currency)}"
                } else {
                    formatMoney(item.amount, item.currency)
                }
            }

            row.addView(
                detailText,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10)
                }
            )

            val durationGroup = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            }
            val divider = View(this).apply {
                setBackgroundColor(getColor(R.color.border_subtle))
            }
            val durationText = TextView(this).apply {
                setTextColor(getColor(R.color.ink_muted))
                textSize = 12f
                text = formatRelativeTime(item.createdAtMs)
                gravity = android.view.Gravity.END
            }

            durationGroup.addView(divider, LinearLayout.LayoutParams(dp(1), dp(20)))
            durationGroup.addView(
                durationText,
                LinearLayout.LayoutParams(dp(27), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(5)
                }
            )
            row.addView(
                durationGroup,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            historyListContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) dp(14) else dp(6)
                }
            )
        }
    }

    private fun formatMoney(amount: String, currency: String): String {
        val symbol = when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "ZAR" -> "R "
            else -> ""
        }
        return if (symbol.isNotEmpty()) "$symbol$amount" else "$amount ${currency.uppercase()}"
    }

    private fun formatRelativeTime(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Unknown"
        val diffMs = (System.currentTimeMillis() - createdAtMs).coerceAtLeast(0L)
        val minutes = diffMs / (60 * 1000)
        val hours = diffMs / (60 * 60 * 1000)
        val days = diffMs / (24 * 60 * 60 * 1000)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> formatAbsoluteTime(createdAtMs)
        }
    }

    private fun formatAbsoluteTime(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Unknown"
        val format = SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault())
        return format.format(Date(createdAtMs))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
