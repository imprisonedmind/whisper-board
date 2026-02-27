package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.config.AppModeConfig
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthHomeActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var loginButton: Button
    private lateinit var createButton: Button
    private lateinit var testerButton: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppModeConfig.isAuthRequired) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth_home)

        loginButton = findViewById(R.id.authHomeLoginButton)
        createButton = findViewById(R.id.authHomeCreateButton)
        testerButton = findViewById(R.id.authHomeTesterButton)
        progress = findViewById(R.id.authHomeProgress)

        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginEmailActivity::class.java))
        }

        createButton.setOnClickListener {
            startActivity(Intent(this, CreateAccountActivity::class.java))
        }

        testerButton.setOnClickListener {
            showTesterLoginDialog()
        }
    }

    private fun setLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        createButton.isEnabled = !loading
        testerButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showTesterLoginDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.auth_tester_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.auth_tester_key_title)
            .setView(input)
            .setPositiveButton(R.string.auth_tester_key_submit) { _, _ ->
                val secretKey = input.text.toString().trim()
                if (secretKey.isEmpty()) {
                    Toast.makeText(this, R.string.auth_tester_key_missing, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                loginAsTester(secretKey)
            }
            .setNegativeButton(R.string.auth_tester_key_cancel, null)
            .show()
    }

    private fun loginAsTester(secretKey: String) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("secretKey", secretKey)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@AuthHomeActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/tester-login")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Tester login failed" })
                }

                val json = JSONObject(body)
                val accessToken = json.getString("accessToken")
                val refreshToken = json.optString("refreshToken", "").ifBlank { null }
                val expiresAt = json.getLong("expiresAt")
                val verifiedEmail = json.optString("email", "").ifBlank {
                    "playstore-reviewer@yapboard.app"
                }
                AuthStore.saveSession(this@AuthHomeActivity, accessToken, refreshToken, expiresAt, verifiedEmail)

                val intent = Intent(this@AuthHomeActivity, AuthGateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    this@AuthHomeActivity,
                    getString(R.string.auth_tester_verify_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }
}
