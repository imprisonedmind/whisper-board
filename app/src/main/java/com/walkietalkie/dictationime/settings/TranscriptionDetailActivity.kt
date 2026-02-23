package com.walkietalkie.dictationime.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.EditText
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.content.ContextCompat

class TranscriptionDetailActivity : AppCompatActivity() {
    private val httpClient = OkHttpClient()
    private lateinit var markInaccurateButton: Button
    private lateinit var inaccurateStatusText: TextView
    private lateinit var requestId: String
    private var isInaccurate = false
    private var inaccurateReason: String? = null
    private var inaccurateDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcription_detail)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        val transcript = intent.getStringExtra(EXTRA_TRANSCRIPT).orEmpty()
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0).coerceAtLeast(0)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val createdAtLabel = intent.getStringExtra(EXTRA_CREATED_AT_LABEL).orEmpty()
        isInaccurate = intent.getBooleanExtra(EXTRA_IS_INACCURATE, false)
        inaccurateReason = intent.getStringExtra(EXTRA_INACCURATE_REASON).orEmpty().ifBlank { null }

        findViewById<TextView>(R.id.transcriptText).text =
            if (transcript.isBlank()) "(No transcript text)" else transcript
        findViewById<TextView>(R.id.metaTime).text =
            getString(R.string.history_detail_meta_time, createdAtLabel.ifBlank { "Unknown" })
        findViewById<TextView>(R.id.metaDuration).text =
            getString(R.string.history_detail_meta_duration, formatDuration(durationSeconds))
        findViewById<TextView>(R.id.metaRequestId).text =
            getString(R.string.history_detail_meta_request, requestId.ifBlank { "N/A" })

        val copyButton = findViewById<Button>(R.id.copyButton)
        copyButton.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

        markInaccurateButton = findViewById(R.id.markInaccurateButton)
        inaccurateStatusText = findViewById(R.id.inaccurateStatusText)
        markInaccurateButton.backgroundTintList = null
        markInaccurateButton.setBackgroundResource(R.drawable.bg_danger_button_states)
        markInaccurateButton.setTextColor(android.graphics.Color.WHITE)
        renderInaccurateState()

        markInaccurateButton.setOnClickListener {
            showInaccurateReasonDialog()
        }

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("transcript", transcript))
            Toast.makeText(this, R.string.history_copy_success, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    private fun showInaccurateReasonDialog() {
        if (requestId.isBlank() || isInaccurate) return

        inaccurateDialog?.dismiss()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_mark_inaccurate)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val input = dialog.findViewById<EditText>(R.id.dialogReasonInput)
        val sanitizedReason = inaccurateReason?.takeUnless {
            it.equals("null", ignoreCase = true) || it.isBlank()
        }
        input.setText(sanitizedReason.orEmpty())

        val cancelButton = dialog.findViewById<Button>(R.id.dialogCancelButton)
        val skipButton = dialog.findViewById<Button>(R.id.dialogSkipButton)
        val submitButton = dialog.findViewById<Button>(R.id.dialogSubmitButton)

        cancelButton.backgroundTintList = null
        cancelButton.setBackgroundResource(R.drawable.bg_danger_button_states)
        cancelButton.setTextColor(android.graphics.Color.WHITE)

        skipButton.backgroundTintList = null
        skipButton.setBackgroundResource(R.drawable.bg_card)
        skipButton.setTextColor(ContextCompat.getColor(this, R.color.ink))

        submitButton.backgroundTintList = null
        submitButton.setBackgroundResource(R.drawable.bg_primary_button)
        submitButton.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        skipButton.setOnClickListener {
            dialog.dismiss()
            markAsInaccurate(null)
        }
        submitButton.setOnClickListener {
            dialog.dismiss()
            markAsInaccurate(input.text?.toString()?.trim().orEmpty().ifBlank { null })
        }

        dialog.setOnDismissListener {
            if (inaccurateDialog === dialog) inaccurateDialog = null
        }
        inaccurateDialog = dialog
        dialog.show()
    }

    private fun markAsInaccurate(reason: String?) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Toast.makeText(this, R.string.auth_backend_missing, Toast.LENGTH_SHORT).show()
            return
        }
        if (requestId.isBlank() || isInaccurate) return

        markInaccurateButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val token = AuthSessionManager.getValidAccessToken(this@TranscriptionDetailActivity)
                if (token.isNullOrBlank()) {
                    AuthStore.clearSession(this@TranscriptionDetailActivity)
                    startActivity(Intent(this@TranscriptionDetailActivity, LoginEmailActivity::class.java))
                    finish()
                    return@launch
                }

                val payload = JSONObject().apply {
                    if (!reason.isNullOrBlank()) {
                        put("reason", reason)
                    }
                }.toString()

                val request = Request.Builder()
                    .url("$baseUrl/credits/history/$requestId/mark-inaccurate")
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val body = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Mark inaccurate failed: ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                }

                val json = JSONObject(body)
                isInaccurate = json.optBoolean("isInaccurate", true)
                inaccurateReason = json.optString("inaccurateReason", "").let {
                    if (it.isBlank() || it.equals("null", ignoreCase = true)) reason else it
                }
                renderInaccurateState()
                Toast.makeText(
                    this@TranscriptionDetailActivity,
                    R.string.history_mark_inaccurate_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                markInaccurateButton.isEnabled = true
                Toast.makeText(
                    this@TranscriptionDetailActivity,
                    R.string.history_mark_inaccurate_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderInaccurateState() {
        if (isInaccurate) {
            markInaccurateButton.isEnabled = false
            markInaccurateButton.text = getString(R.string.history_mark_inaccurate_done)
            inaccurateStatusText.text =
                if (inaccurateReason.isNullOrBlank()) {
                    getString(R.string.history_mark_inaccurate_status)
                } else {
                    getString(R.string.history_mark_inaccurate_status_with_reason, inaccurateReason)
                }
            inaccurateStatusText.visibility = View.VISIBLE
        } else {
            markInaccurateButton.isEnabled = true
            markInaccurateButton.text = getString(R.string.history_mark_inaccurate_button)
            inaccurateStatusText.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_CREDITS_USED_MINUTES = "extra_credits_used_minutes"
        const val EXTRA_CREATED_AT_MS = "extra_created_at_ms"
        const val EXTRA_CREATED_AT_LABEL = "extra_created_at_label"
        const val EXTRA_IS_INACCURATE = "extra_is_inaccurate"
        const val EXTRA_INACCURATE_REASON = "extra_inaccurate_reason"
    }

    override fun onDestroy() {
        inaccurateDialog?.dismiss()
        inaccurateDialog = null
        super.onDestroy()
    }
}
