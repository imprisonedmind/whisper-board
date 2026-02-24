package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.Button
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

class LoginOtpActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var otpInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var resendButton: TextView
    private lateinit var helperText: TextView
    private lateinit var progress: View
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppModeConfig.isAuthRequired) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login_otp)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        if (email.isBlank()) {
            finish()
            return
        }

        otpInput = findViewById(R.id.otpInput)
        otpInput.filters = arrayOf(InputFilter.LengthFilter(6))
        verifyButton = findViewById(R.id.verifyCodeButton)
        resendButton = findViewById(R.id.resendCodeButton)
        helperText = findViewById(R.id.loginOtpHelper)
        progress = findViewById(R.id.loginOtpProgress)

        findViewById<TextView>(R.id.loginOtpEmail).text = email

        verifyButton.setOnClickListener {
            verifyOtp()
        }

        resendButton.setOnClickListener {
            resendCode()
        }
    }

    private fun verifyOtp() {
        val otp = otpInput.text.toString().trim()
        if (otp.length != 6) {
            Toast.makeText(this, R.string.auth_invalid_code, Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        helperText.text = getString(R.string.auth_verifying_code)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("email", email)
                    .put("otp", otp)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@LoginOtpActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/verify-otp")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Verification failed" })
                }

                val json = JSONObject(body)
                val accessToken = json.getString("accessToken")
                val refreshToken = json.optString("refreshToken", "").ifBlank { null }
                val expiresAt = json.getLong("expiresAt")
                val verifiedEmail = json.optString("email", email).ifBlank { email }
                AuthStore.saveSession(this@LoginOtpActivity, accessToken, refreshToken, expiresAt, verifiedEmail)

                val intent = Intent(this@LoginOtpActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                helperText.text = getString(R.string.auth_otp_helper)
                Toast.makeText(
                    this@LoginOtpActivity,
                    getString(R.string.auth_verify_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resendCode() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        helperText.text = getString(R.string.auth_resending_code)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("email", email)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@LoginOtpActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/request-otp")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to resend")
                }

                helperText.text = getString(R.string.auth_otp_sent_again)
            } catch (e: Exception) {
                helperText.text = getString(R.string.auth_otp_helper)
                Toast.makeText(
                    this@LoginOtpActivity,
                    getString(R.string.auth_send_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        verifyButton.isEnabled = !loading
        resendButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
