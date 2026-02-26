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
            val target = if (!AppModeConfig.isAuthRequired) {
                MainActivity::class.java
            } else if (AuthStore.isSignedIn(this@AuthGateActivity)) {
                val token = AuthSessionManager.getValidAccessToken(this@AuthGateActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@AuthGateActivity)
                    LoginEmailActivity::class.java
                } else {
                    resolveSignedInTarget(token)
                }
            } else {
                LoginEmailActivity::class.java
            }

            startActivity(Intent(this@AuthGateActivity, target))
            finish()
        }
    }

    private suspend fun resolveSignedInTarget(token: String): Class<*> {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) return MainActivity::class.java

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
            if (json.optBoolean("completed", false)) MainActivity::class.java else OnboardingActivity::class.java
        } catch (_: Exception) {
            MainActivity::class.java
        }
    }
}
