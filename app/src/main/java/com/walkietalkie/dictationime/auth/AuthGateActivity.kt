package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.config.AppModeConfig
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AuthGateActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val targetIntent = if (!AppModeConfig.isAuthRequired) {
                Intent(this@AuthGateActivity, MainActivity::class.java)
            } else if (AuthStore.isSignedIn(this@AuthGateActivity)) {
                val token = AuthSessionManager.getValidAccessToken(this@AuthGateActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@AuthGateActivity)
                    Intent(this@AuthGateActivity, AuthHomeActivity::class.java)
                } else {
                    resolveSignedInTarget(token)
                }
            } else {
                Intent(this@AuthGateActivity, AuthHomeActivity::class.java)
            }

            startActivity(targetIntent)
            finish()
        }
    }

    private suspend fun resolveSignedInTarget(token: String): Intent {
        OnboardingRequirements.missingStep(this@AuthGateActivity)?.let { missingStep ->
            return onboardingIntent(missingStep)
        }

        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return Intent(this@AuthGateActivity, MainActivity::class.java)

        return try {
            val request = Request.Builder()
                .url("$baseUrl/profiles/onboarding")
                .header("Authorization", "Bearer $token")
                .build()
            val body = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Onboarding status failed: ${response.code}")
                    }
                    response.body?.string().orEmpty()
                }
            }
            val json = JSONObject(body)
            if (json.optBoolean("completed", false)) {
                Intent(this@AuthGateActivity, MainActivity::class.java)
            } else {
                onboardingIntent()
            }
        } catch (_: Exception) {
            Intent(this@AuthGateActivity, MainActivity::class.java)
        }
    }

    private fun onboardingIntent(startStep: String? = null): Intent {
        return Intent(this@AuthGateActivity, OnboardingActivity::class.java).apply {
            if (!startStep.isNullOrBlank()) {
                putExtra(OnboardingActivity.EXTRA_START_STEP, startStep)
            }
        }
    }
}
