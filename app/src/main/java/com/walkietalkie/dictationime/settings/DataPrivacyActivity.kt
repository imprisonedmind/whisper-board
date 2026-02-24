package com.walkietalkie.dictationime.settings

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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
    companion object {
        private const val TRACKING_PREFS_NAME = "walkie_data_tracking"
        private const val KEY_TRACK_APP_PROFILES = "track_app_profiles"
        private const val KEY_TRACK_TRANSCRIPTION_TEXT = "track_transcription_text"
        private const val KEY_TRACK_AUDIO_FILES = "track_audio_files"
    }

    private val client = OkHttpClient()
    private lateinit var statusText: TextView
    private lateinit var deleteDataButton: Button
    private lateinit var cancelDeletionButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var appProfilesTrackingSwitch: SwitchCompat
    private lateinit var transcriptionTrackingSwitch: SwitchCompat
    private lateinit var audioFilesTrackingSwitch: SwitchCompat
    private var suppressTrackingToggleCallback = false
    private var trackingConfirmDialog: Dialog? = null
    private var hasRequestedDeletion = false
    private var hasRenderedCache = false
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
        appProfilesTrackingSwitch = findViewById(R.id.appProfilesTrackingSwitch)
        transcriptionTrackingSwitch = findViewById(R.id.transcriptionTrackingSwitch)
        audioFilesTrackingSwitch = findViewById(R.id.audioFilesTrackingSwitch)

        applyDangerStyle(deleteDataButton)
        applyDangerStyle(cancelDeletionButton)
        bindTrackingPreferenceSwitch(
            toggle = appProfilesTrackingSwitch,
            key = KEY_TRACK_APP_PROFILES,
            disableBodyRes = R.string.data_tracking_disable_app_profiles_body
        )
        bindTrackingPreferenceSwitch(
            toggle = transcriptionTrackingSwitch,
            key = KEY_TRACK_TRANSCRIPTION_TEXT,
            disableBodyRes = R.string.data_tracking_disable_transcription_body
        )
        bindTrackingPreferenceSwitch(audioFilesTrackingSwitch, KEY_TRACK_AUDIO_FILES)

        findViewById<ImageButton>(R.id.dataPrivacyBackButton).setOnClickListener { finish() }
        deleteDataButton.setOnClickListener { confirmDeletionRequest() }
        cancelDeletionButton.setOnClickListener { confirmCancelDeletionRequest() }

        DataDeletionStatusDataCache.getCachedStatus(this)?.let {
            hasRenderedCache = true
            updateStatusUi(requestedAt = it.requestedAt, scheduledAt = it.scheduledAt)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchDeletionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingConfirmDialog?.dismiss()
        trackingConfirmDialog = null
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

        if (!hasRenderedCache) {
            setLoading(true, null)
        }
        lifecycleScope.launch {
            try {
                val fetched = DataDeletionStatusDataCache.fetchAndCacheStatus(this@DataPrivacyActivity, baseUrl)
                if (fetched != null) {
                    hasRenderedCache = true
                    updateStatusUi(
                        requestedAt = fetched.requestedAt,
                        scheduledAt = fetched.scheduledAt
                    )
                    return@launch
                }
                throw IllegalStateException("Failed to fetch status")
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
                val requestedAt = json.optLongOrNull("requestedAt")
                val scheduledAt = json.optLongOrNull("scheduledAt")
                DataDeletionStatusDataCache.cacheStatus(this@DataPrivacyActivity, requestedAt, scheduledAt)
                updateStatusUi(requestedAt = requestedAt, scheduledAt = scheduledAt)
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
                val requestedAt = json.optLongOrNull("requestedAt")
                val scheduledAt = json.optLongOrNull("scheduledAt")
                DataDeletionStatusDataCache.cacheStatus(this@DataPrivacyActivity, requestedAt, scheduledAt)
                updateStatusUi(requestedAt = requestedAt, scheduledAt = scheduledAt)
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

    private fun bindTrackingPreferenceSwitch(
        toggle: SwitchCompat,
        key: String,
        disableBodyRes: Int? = null
    ) {
        val prefs = getSharedPreferences(TRACKING_PREFS_NAME, MODE_PRIVATE)
        setTrackingSwitchChecked(toggle, prefs.getBoolean(key, true))
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (suppressTrackingToggleCallback) {
                return@setOnCheckedChangeListener
            }
            if (!isChecked && disableBodyRes != null) {
                showDisableTrackingConfirmation(
                    body = getString(disableBodyRes),
                    onConfirm = {
                        prefs.edit().putBoolean(key, false).apply()
                    },
                    onCancel = {
                        setTrackingSwitchChecked(toggle, true)
                    }
                )
                return@setOnCheckedChangeListener
            }
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }

    private fun setTrackingSwitchChecked(toggle: SwitchCompat, checked: Boolean) {
        suppressTrackingToggleCallback = true
        toggle.isChecked = checked
        suppressTrackingToggleCallback = false
    }

    private fun showDisableTrackingConfirmation(
        body: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        trackingConfirmDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_data_tracking_disable)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.dialogTitle).text =
            getString(R.string.data_tracking_disable_title)
        dialog.findViewById<TextView>(R.id.dialogBody).text = body
        val keepEnabledButton = dialog.findViewById<Button>(R.id.dialogCancelButton)
        val turnOffButton = dialog.findViewById<Button>(R.id.dialogSubmitButton)

        var actionHandled = false
        keepEnabledButton.setOnClickListener {
            actionHandled = true
            dialog.dismiss()
            onCancel()
        }
        turnOffButton.setOnClickListener {
            actionHandled = true
            dialog.dismiss()
            onConfirm()
        }

        dialog.setOnCancelListener {
            if (!actionHandled) {
                onCancel()
            }
        }
        dialog.setOnDismissListener {
            if (trackingConfirmDialog === dialog) {
                trackingConfirmDialog = null
            }
        }
        trackingConfirmDialog = dialog
        dialog.show()
    }

    private fun applyDangerStyle(button: Button) {
        ViewCompat.setBackgroundTintList(button, null)
        button.setBackgroundResource(R.drawable.bg_danger_button_states)
        button.setTextColor(Color.WHITE)
        button.alpha = 1f
    }

    private fun applyNeutralStyle(button: Button, disabled: Boolean) {
        ViewCompat.setBackgroundTintList(button, null)
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
