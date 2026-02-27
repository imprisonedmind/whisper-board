package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
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

class LoginOtpActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var otpInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var resendButton: TextView
    private lateinit var helperText: TextView
    private lateinit var progress: View
    private lateinit var email: String
    private lateinit var flow: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppModeConfig.isAuthRequired) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login_otp)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        flow = intent.getStringExtra(EXTRA_FLOW).orEmpty().ifBlank { FLOW_LOGIN }
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

                if (flow == FLOW_LOGIN) {
                    payload.put("deviceId", AuthStore.getOrCreateDeviceId(this@LoginOtpActivity))
                }

                val endpoint = if (flow == FLOW_SIGNUP) {
                    "$baseUrl/auth/signup/verify-otp"
                } else {
                    "$baseUrl/auth/verify-otp"
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    AuthApiLogger.logHttpFailure(endpoint, response.code, body)
                    val backendMessage = AuthApiLogger.parseBackendMessage(
                        response.code,
                        body,
                        getString(R.string.auth_verify_failed)
                    )
                    helperText.text = backendMessage
                    Toast.makeText(this@LoginOtpActivity, backendMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (flow == FLOW_SIGNUP) {
                    val json = JSONObject(body)
                    val ticket = json.optString("verificationTicket", "")
                    if (ticket.isBlank()) {
                        throw IllegalStateException("Missing verification ticket")
                    }
                    startActivity(
                        Intent(this@LoginOtpActivity, SetPasswordActivity::class.java)
                            .putExtra(SetPasswordActivity.EXTRA_VERIFICATION_TICKET, ticket)
                    )
                    finish()
                    return@launch
                }

                val json = JSONObject(body)
                val accessToken = json.getString("accessToken")
                val refreshToken = json.optString("refreshToken", "").ifBlank { null }
                val expiresAt = json.getLong("expiresAt")
                val verifiedEmail = json.optString("email", email).ifBlank { email }
                AuthStore.saveSession(this@LoginOtpActivity, accessToken, refreshToken, expiresAt, verifiedEmail)

                val intent = Intent(this@LoginOtpActivity, AuthGateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                val endpoint = if (flow == FLOW_SIGNUP) {
                    "$baseUrl/auth/signup/verify-otp"
                } else {
                    "$baseUrl/auth/verify-otp"
                }
                AuthApiLogger.logException(endpoint, e)
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

                val endpoint = if (flow == FLOW_SIGNUP) {
                    "$baseUrl/auth/signup/request-otp"
                } else {
                    "$baseUrl/auth/request-otp"
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    AuthApiLogger.logHttpFailure(endpoint, response.code, body)
                    val backendMessage = AuthApiLogger.parseBackendMessage(
                        response.code,
                        body,
                        getString(R.string.auth_send_failed)
                    )
                    helperText.text = backendMessage
                    Toast.makeText(this@LoginOtpActivity, backendMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }

                helperText.text = getString(R.string.auth_otp_sent_again)
            } catch (e: Exception) {
                val endpoint = if (flow == FLOW_SIGNUP) {
                    "$baseUrl/auth/signup/request-otp"
                } else {
                    "$baseUrl/auth/request-otp"
                }
                AuthApiLogger.logException(endpoint, e)
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
        const val EXTRA_FLOW = "extra_flow"
        const val FLOW_LOGIN = "login"
        const val FLOW_SIGNUP = "signup"
    }
}
