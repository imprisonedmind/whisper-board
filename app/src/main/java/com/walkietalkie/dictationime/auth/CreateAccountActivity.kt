package com.walkietalkie.dictationime.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
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

class CreateAccountActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var emailInput: EditText
    private lateinit var createButton: Button
    private lateinit var switchToLoginButton: TextView
    private lateinit var helperText: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppModeConfig.isAuthRequired) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_create_account)

        emailInput = findViewById(R.id.createAccountEmailInput)
        createButton = findViewById(R.id.createAccountButton)
        switchToLoginButton = findViewById(R.id.createAccountSwitchToLoginButton)
        helperText = findViewById(R.id.createAccountHelper)
        progress = findViewById(R.id.createAccountProgress)

        createButton.setOnClickListener {
            startCreateFlow()
        }

        switchToLoginButton.setOnClickListener {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
        }
    }

    private fun startCreateFlow() {
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
                    .put("deviceId", AuthStore.getOrCreateDeviceId(this@CreateAccountActivity))
                    .toString()

                val request = Request.Builder()
                    .url("$baseUrl/auth/signup/request-otp")
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
                        getString(R.string.auth_send_failed)
                    )
                    helperText.text = backendMessage
                    Toast.makeText(this@CreateAccountActivity, backendMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }

                startActivity(
                    Intent(this@CreateAccountActivity, LoginOtpActivity::class.java)
                        .putExtra(LoginOtpActivity.EXTRA_EMAIL, email)
                        .putExtra(LoginOtpActivity.EXTRA_FLOW, LoginOtpActivity.FLOW_SIGNUP)
                )
                finish()
            } catch (e: Exception) {
                AuthApiLogger.logException("$baseUrl/auth/signup/request-otp", e)
                helperText.text = getString(R.string.auth_create_account_helper)
                Toast.makeText(
                    this@CreateAccountActivity,
                    getString(R.string.auth_send_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        createButton.isEnabled = !loading
        switchToLoginButton.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
