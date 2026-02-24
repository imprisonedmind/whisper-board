package com.walkietalkie.dictationime.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.walkietalkie.dictationime.BuildConfig
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.auth.AuthSessionManager
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class DataPrivacyActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var statusText: TextView
    private lateinit var deleteDataButton: Button
    private lateinit var cancelDeletionButton: Button
    private lateinit var progress: ProgressBar
    private var hasRequestedDeletion = false
    private val dateFormatter: DateFormat by lazy { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthStore.isSignedIn(this)) {
            startActivity(Intent(this, LoginEmailActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_data_privacy)
        statusText = findViewById(R.id.dataPrivacyStatus)
        deleteDataButton = findViewById(R.id.deleteDataButton)
        cancelDeletionButton = findViewById(R.id.cancelDeletionButton)
        progress = findViewById(R.id.dataPrivacyProgress)

        applyDangerStyle(deleteDataButton)
        applyDangerStyle(cancelDeletionButton)

        findViewById<ImageButton>(R.id.dataPrivacyBackButton).setOnClickListener { finish() }
        deleteDataButton.setOnClickListener { confirmDeletionRequest() }
        cancelDeletionButton.setOnClickListener { confirmCancelDeletionRequest() }
    }

    override fun onResume() {
        super.onResume()
        fetchDeletionStatus()
    }

    private fun confirmDeletionRequest() {
        AlertDialog.Builder(this)
            .setTitle(R.string.data_privacy_confirm_title)
            .setMessage(R.string.data_privacy_confirm_body)
            .setPositiveButton(R.string.data_privacy_confirm_action) { _, _ ->
                submitDeletionRequest()
            }
            .setNegativeButton(R.string.data_privacy_cancel_action, null)
            .show()
    }

    private fun fetchDeletionStatus() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, null)
        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@DataPrivacyActivity)
                if (token.isNullOrBlank()) {
                    throw IllegalStateException("Missing token")
                }

                val request = Request.Builder()
                    .url("$baseUrl/profiles/data-deletion")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Failed to fetch status" })
                }

                val json = JSONObject(body)
                updateStatusUi(
                    requestedAt = json.optLongOrNull("requestedAt"),
                    scheduledAt = json.optLongOrNull("scheduledAt")
                )
            } catch (_: Exception) {
                statusText.text = getString(R.string.data_privacy_status_none)
                hasRequestedDeletion = false
                deleteDataButton.text = getString(R.string.data_privacy_request_button)
                cancelDeletionButton.visibility = View.GONE
                updateActionButtonsEnabled(loading = false)
            } finally {
                setLoading(false, null)
            }
        }
    }

    private fun submitDeletionRequest() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, getString(R.string.data_privacy_requesting))
        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@DataPrivacyActivity)
                if (token.isNullOrBlank()) {
                    throw IllegalStateException("Missing token")
                }

                val request = Request.Builder()
                    .url("$baseUrl/profiles/data-deletion")
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Failed to submit deletion request" })
                }

                val json = JSONObject(body)
                updateStatusUi(
                    requestedAt = json.optLongOrNull("requestedAt"),
                    scheduledAt = json.optLongOrNull("scheduledAt")
                )
            } catch (_: Exception) {
                Toast.makeText(this@DataPrivacyActivity, R.string.data_privacy_request_failed, Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false, null)
            }
        }
    }

    private fun updateStatusUi(requestedAt: Long?, scheduledAt: Long?) {
        if (requestedAt == null || scheduledAt == null) {
            hasRequestedDeletion = false
            statusText.text = getString(R.string.data_privacy_status_none)
            deleteDataButton.text = getString(R.string.data_privacy_request_button)
            cancelDeletionButton.visibility = View.GONE
            applyDangerStyle(deleteDataButton)
            updateActionButtonsEnabled(loading = false)
            return
        }

        hasRequestedDeletion = true
        val requestedLabel = dateFormatter.format(Date(requestedAt))
        val scheduledLabel = dateFormatter.format(Date(scheduledAt))
        statusText.text = getString(R.string.data_privacy_status_requested, requestedLabel, scheduledLabel)
        deleteDataButton.text = getString(R.string.data_privacy_requested_button)
        cancelDeletionButton.visibility = View.VISIBLE
        applyNeutralStyle(deleteDataButton, disabled = true)
        applyDangerStyle(cancelDeletionButton)
        updateActionButtonsEnabled(loading = false)
    }

    private fun confirmCancelDeletionRequest() {
        AlertDialog.Builder(this)
            .setTitle(R.string.data_privacy_cancel_confirm_title)
            .setMessage(R.string.data_privacy_cancel_confirm_body)
            .setPositiveButton(R.string.data_privacy_cancel_confirm_action) { _, _ ->
                cancelDeletionRequest()
            }
            .setNegativeButton(R.string.data_privacy_cancel_action, null)
            .show()
    }

    private fun cancelDeletionRequest() {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, getString(R.string.data_privacy_cancelling))
        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@DataPrivacyActivity)
                if (token.isNullOrBlank()) {
                    throw IllegalStateException("Missing token")
                }

                val request = Request.Builder()
                    .url("$baseUrl/profiles/data-deletion/cancel")
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "Failed to cancel deletion request" })
                }

                val json = JSONObject(body)
                updateStatusUi(
                    requestedAt = json.optLongOrNull("requestedAt"),
                    scheduledAt = json.optLongOrNull("scheduledAt")
                )
            } catch (_: Exception) {
                Toast.makeText(this@DataPrivacyActivity, R.string.data_privacy_cancel_failed, Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false, null)
            }
        }
    }

    private fun setLoading(loading: Boolean, statusOverride: String?) {
        applyCurrentStateStyles()
        updateActionButtonsEnabled(loading)
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (!statusOverride.isNullOrBlank()) {
            statusText.text = statusOverride
        }
    }

    private fun updateActionButtonsEnabled(loading: Boolean) {
        deleteDataButton.isEnabled = !loading && !hasRequestedDeletion
        cancelDeletionButton.isEnabled = !loading && hasRequestedDeletion
    }

    private fun applyCurrentStateStyles() {
        if (hasRequestedDeletion) {
            applyNeutralStyle(deleteDataButton, disabled = true)
            applyDangerStyle(cancelDeletionButton)
        } else {
            applyDangerStyle(deleteDataButton)
            applyDangerStyle(cancelDeletionButton)
        }
    }

    private fun applyDangerStyle(button: Button) {
        button.backgroundTintList = null
        button.setBackgroundResource(R.drawable.bg_danger_button_states)
        button.setTextColor(Color.WHITE)
        button.alpha = 1f
    }

    private fun applyNeutralStyle(button: Button, disabled: Boolean) {
        button.backgroundTintList = null
        button.setBackgroundResource(R.drawable.bg_card)
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (disabled) R.color.ink_muted else R.color.ink
            )
        )
        button.alpha = if (disabled) 0.72f else 1f
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (isNull(key)) return null
        return optLong(key, 0L).takeIf { it > 0L }
    }
}
