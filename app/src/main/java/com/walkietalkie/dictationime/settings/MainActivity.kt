package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthSessionManager
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import com.walkietalkie.dictationime.ui.RoundedCircularProgressView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private companion object {
        const val BUY_PROMPT_THRESHOLD_RATIO = 0.5f
    }

    private val httpClient = OkHttpClient()
    private lateinit var creditsRing: RoundedCircularProgressView
    private lateinit var creditsBalanceText: TextView
    private lateinit var creditsUsageValue: TextView
    private lateinit var historyListContainer: LinearLayout
    private lateinit var historyEmptyText: TextView
    private lateinit var creditStoreCard: LinearLayout
    private lateinit var buyCreditsButton: Button

    private data class UsageHistoryItem(
        val requestId: String,
        val transcript: String,
        val durationSeconds: Int,
        val createdAtMs: Long,
        val creditsUsedMinutes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val profileButton: ImageButton = findViewById(R.id.profileButton)
        val creditsCard: LinearLayout = findViewById(R.id.creditsCard)
        val creditsInfoIcon: ImageView = findViewById(R.id.creditsInfoIcon)
        creditStoreCard = findViewById(R.id.creditStoreCard)
        buyCreditsButton = findViewById(R.id.buyCreditsButton)
        historyListContainer = findViewById(R.id.historyListContainer)
        historyEmptyText = findViewById(R.id.historyEmptyText)
        creditsRing = findViewById(R.id.creditsRing)
        creditsBalanceText = findViewById(R.id.creditsBalanceText)
        creditsUsageValue = findViewById(R.id.creditsUsageValue)

        applyCreditsState(0, 0)
        renderHistory(emptyList())

        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        creditsCard.setOnClickListener {
            openCreditStore()
        }
        creditsInfoIcon.setOnClickListener {
            openCreditStore()
        }

        buyCreditsButton.setOnClickListener {
            openCreditStore()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCredits()
        loadHistory()
        lifecycleScope.launch {
            CreditStoreDataCache.prefetch(this@MainActivity)
        }
    }

    private fun openCreditStore() {
        lifecycleScope.launch {
            CreditStoreDataCache.prefetch(this@MainActivity)
        }
        startActivity(Intent(this, CreditStoreActivity::class.java))
    }

    private fun loadCredits() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return

        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@MainActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginEmailActivity::class.java))
                    finish()
                    return@launch
                }
                val request = Request.Builder()
                    .url("$baseUrl/credits/balance")
                    .header("Authorization", "Bearer $token")
                    .build()
                val body = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Credits fetch failed: ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                }
                val json = JSONObject(body)
                val minutesRemaining = json.optInt("minutesRemaining", 0)
                val usageMinutes = json.optInt("usageThisWeekMinutes", 0)

                applyCreditsState(minutesRemaining, usageMinutes)
            } catch (_: Exception) {
                // Keep existing UI values on failure.
            }
        }
    }

    private fun applyCreditsState(minutesRemaining: Int, usageMinutes: Int) {
        val safeMinutesRemaining = minutesRemaining.coerceAtLeast(0)
        val safeUsageMinutes = usageMinutes.coerceAtLeast(0)
        val formatter = NumberFormat.getIntegerInstance()
        creditsBalanceText.text = formatter.format(safeMinutesRemaining)
        creditsUsageValue.text = getString(R.string.credits_usage_value_format, safeUsageMinutes)

        val total = safeMinutesRemaining + safeUsageMinutes
        val progress = if (total > 0) safeMinutesRemaining.toFloat() / total else 0f
        creditsRing.setProgress(progress)

        val showCreditStoreCard = total > 0 && progress < BUY_PROMPT_THRESHOLD_RATIO
        creditStoreCard.visibility = if (showCreditStoreCard) View.VISIBLE else View.GONE
    }

    private fun loadHistory() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return

        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@MainActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@MainActivity)
                    startActivity(Intent(this@MainActivity, LoginEmailActivity::class.java))
                    finish()
                    return@launch
                }
                val request = Request.Builder()
                    .url("$baseUrl/credits/history")
                    .header("Authorization", "Bearer $token")
                    .build()
                val body = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("History fetch failed: ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                }
                val json = JSONObject(body)
                val events = json.optJSONArray("events")
                val items = mutableListOf<UsageHistoryItem>()
                if (events != null) {
                    for (i in 0 until events.length()) {
                        val item = events.getJSONObject(i)
                        items += UsageHistoryItem(
                            requestId = item.optString("requestId", ""),
                            transcript = item.optString("transcript", "").trim(),
                            durationSeconds = item.optInt("durationSeconds", 0).coerceAtLeast(0),
                            createdAtMs = item.optLong("createdAt", 0L),
                            creditsUsedMinutes = item.optInt("creditsUsedMinutes", 0).coerceAtLeast(0)
                        )
                    }
                }
                renderHistory(items)
            } catch (_: Exception) {
                // Keep existing history UI on failure.
            }
        }
    }

    private fun renderHistory(items: List<UsageHistoryItem>) {
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
                background = getDrawable(R.drawable.bg_history_row)
                setPadding(dp(10), dp(9), dp(10), dp(9))
                isClickable = true
                isFocusable = true
            }

            val transcriptText = TextView(this).apply {
                setTextColor(getColor(R.color.ink))
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                text = if (item.transcript.isBlank()) "(No transcript text)" else item.transcript
            }

            row.addView(
                transcriptText,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(10)
                }
            )

            val durationGroup = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val divider = View(this).apply {
                setBackgroundColor(getColor(R.color.border_subtle))
            }
            val durationText = TextView(this).apply {
                setTextColor(getColor(R.color.ink_muted))
                textSize = 12f
                text = formatDuration(item.durationSeconds)
                setPadding(dp(8), 0, 0, 0)
            }

            durationGroup.addView(
                divider,
                LinearLayout.LayoutParams(dp(1), dp(20))
            )
            durationGroup.addView(durationText)
            row.addView(
                durationGroup,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            row.setOnClickListener {
                val intent = Intent(this, TranscriptionDetailActivity::class.java)
                    .putExtra(TranscriptionDetailActivity.EXTRA_REQUEST_ID, item.requestId)
                    .putExtra(TranscriptionDetailActivity.EXTRA_TRANSCRIPT, item.transcript)
                    .putExtra(TranscriptionDetailActivity.EXTRA_DURATION_SECONDS, item.durationSeconds)
                    .putExtra(TranscriptionDetailActivity.EXTRA_CREDITS_USED_MINUTES, item.creditsUsedMinutes)
                    .putExtra(TranscriptionDetailActivity.EXTRA_CREATED_AT_MS, item.createdAtMs)
                    .putExtra(
                        TranscriptionDetailActivity.EXTRA_CREATED_AT_LABEL,
                        formatAbsoluteTime(item.createdAtMs)
                    )
                startActivity(intent)
            }

            historyListContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) dp(14) else dp(6)
                    if (index > 0) {
                        // tighter spacing between bordered rows
                    }
                }
            )
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }

    private fun formatRelativeTime(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Just now"
        val diffMs = (System.currentTimeMillis() - createdAtMs).coerceAtLeast(0L)
        val minutes = diffMs / (60 * 1000)
        val hours = diffMs / (60 * 60 * 1000)
        val days = diffMs / (24 * 60 * 60 * 1000)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }

    private fun formatAbsoluteTime(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Unknown"
        val format = SimpleDateFormat("EEE, d MMM yyyy • HH:mm", Locale.getDefault())
        return format.format(Date(createdAtMs))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
