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
    private lateinit var helperText: TextView
    private lateinit var progress: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_email)

        emailInput = findViewById(R.id.emailInput)
        sendButton = findViewById(R.id.sendCodeButton)
        helperText = findViewById(R.id.loginEmailHelper)
        progress = findViewById(R.id.loginEmailProgress)

        sendButton.setOnClickListener {
            requestOtp()
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
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
