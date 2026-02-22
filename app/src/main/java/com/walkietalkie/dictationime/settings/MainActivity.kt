package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.R
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

class MainActivity : AppCompatActivity() {
    private val httpClient = OkHttpClient()
    private lateinit var creditsRing: RoundedCircularProgressView
    private lateinit var creditsBalanceText: TextView
    private lateinit var creditsUsageValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val profileButton: ImageButton = findViewById(R.id.profileButton)
        val buyCreditsButton: Button = findViewById(R.id.buyCreditsButton)
        creditsRing = findViewById(R.id.creditsRing)
        creditsBalanceText = findViewById(R.id.creditsBalanceText)
        creditsUsageValue = findViewById(R.id.creditsUsageValue)

        applyCreditsState(0, 0)

        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        buyCreditsButton.setOnClickListener {
            startActivity(Intent(this, CreditStoreActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadCredits()
    }

    private fun loadCredits() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return
        val token = AuthStore.getAccessToken(this) ?: return

        lifecycleScope.launch {
            try {
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
    }
}
