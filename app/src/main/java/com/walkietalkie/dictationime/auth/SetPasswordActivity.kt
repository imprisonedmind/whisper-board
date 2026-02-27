package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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

class SetPasswordActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var setPasswordButton: Button
    private lateinit var helperText: TextView
    private lateinit var progress: View
    private lateinit var verificationTicket: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppModeConfig.isAuthRequired) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        verificationTicket = intent.getStringExtra(EXTRA_VERIFICATION_TICKET).orEmpty()
        if (verificationTicket.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_set_password)

        passwordInput = findViewById(R.id.setPasswordInput)
        confirmPasswordInput = findViewById(R.id.setPasswordConfirmInput)
        setPasswordButton = findViewById(R.id.setPasswordButton)
        helperText = findViewById(R.id.setPasswordHelper)
        progress = findViewById(R.id.setPasswordProgress)

        setPasswordButton.setOnClickListener {
            submitPassword()
        }
    }

    private fun submitPassword() {
        val password = passwordInput.text.toString().trim()
        val confirm = confirmPasswordInput.text.toString().trim()

        if (password.length < 8) {
            Toast.makeText(this, R.string.auth_invalid_password, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirm) {
            Toast.makeText(this, R.string.auth_password_mismatch, Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        helperText.text = getString(R.string.auth_setting_password)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("verificationTicket", verificationTicket)
                    .put("password", password)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@SetPasswordActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/signup/set-password")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                val endpoint = request.url.toString()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    AuthApiLogger.logHttpFailure(endpoint, response.code, body)
                    val backendMessage = AuthApiLogger.parseBackendMessage(
                        response.code,
                        body,
                        getString(R.string.auth_set_password_failed)
                    )
                    helperText.text = backendMessage
                    Toast.makeText(this@SetPasswordActivity, backendMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val json = JSONObject(body)
                val accessToken = json.getString("accessToken")
                val refreshToken = json.optString("refreshToken", "").ifBlank { null }
                val expiresAt = json.getLong("expiresAt")
                val verifiedEmail = json.optString("email", "").ifBlank { "" }
                AuthStore.saveSession(this@SetPasswordActivity, accessToken, refreshToken, expiresAt, verifiedEmail)

                val intent = Intent(this@SetPasswordActivity, AuthGateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                AuthApiLogger.logException("$baseUrl/auth/signup/set-password", e)
                helperText.text = getString(R.string.auth_set_password_helper)
                Toast.makeText(
                    this@SetPasswordActivity,
                    getString(R.string.auth_set_password_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        setPasswordButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_VERIFICATION_TICKET = "extra_verification_ticket"
    }
}
