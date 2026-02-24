package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
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
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginEmailActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var testerLoginButton: TextView
    private lateinit var helperText: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_email)

        emailInput = findViewById(R.id.emailInput)
        sendButton = findViewById(R.id.sendCodeButton)
        testerLoginButton = findViewById(R.id.loginAsTesterButton)
        helperText = findViewById(R.id.loginEmailHelper)
        progress = findViewById(R.id.loginEmailProgress)

        sendButton.setOnClickListener {
            requestOtp()
        }

        testerLoginButton.setOnClickListener {
            showTesterLoginDialog()
        }
    }

    private fun requestOtp() {
        val email = emailInput.text.toString().trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }

        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        helperText.text = getString(R.string.auth_sending_code)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("email", email)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@LoginEmailActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/request-otp")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Failed to send code" })
                }

                val intent = Intent(this@LoginEmailActivity, LoginOtpActivity::class.java)
                    .putExtra(LoginOtpActivity.EXTRA_EMAIL, email)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                helperText.text = getString(R.string.auth_email_helper)
                Toast.makeText(
                    this@LoginEmailActivity,
                    getString(R.string.auth_send_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        sendButton.isEnabled = !loading
        testerLoginButton.isEnabled = !loading
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
        helperText.text = getString(R.string.auth_verifying_tester_key)

        lifecycleScope.launch {
            try {
                val payload = JSONObject()
                    .put("secretKey", secretKey)
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@LoginEmailActivity))
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
                    "playstore-reviewer@walkietalkie.app"
                }
                AuthStore.saveSession(this@LoginEmailActivity, accessToken, refreshToken, expiresAt, verifiedEmail)

                val intent = Intent(this@LoginEmailActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                helperText.text = getString(R.string.auth_email_helper)
                Toast.makeText(
                    this@LoginEmailActivity,
                    getString(R.string.auth_tester_verify_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }
}
