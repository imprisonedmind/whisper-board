package com.walkietalkie.dictationime.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.walkietalkie.dictationime.R

class TranscriptionDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcription_detail)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        val transcript = intent.getStringExtra(EXTRA_TRANSCRIPT).orEmpty()
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0).coerceAtLeast(0)
        val creditsUsedMinutes = intent.getIntExtra(EXTRA_CREDITS_USED_MINUTES, 0).coerceAtLeast(0)
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val createdAtLabel = intent.getStringExtra(EXTRA_CREATED_AT_LABEL).orEmpty()

        findViewById<TextView>(R.id.transcriptText).text =
            if (transcript.isBlank()) "(No transcript text)" else transcript
        findViewById<TextView>(R.id.metaTime).text =
            getString(R.string.history_detail_meta_time, createdAtLabel.ifBlank { "Unknown" })
        findViewById<TextView>(R.id.metaDuration).text =
            getString(R.string.history_detail_meta_duration, formatDuration(durationSeconds))
        findViewById<TextView>(R.id.metaCredits).text =
            getString(R.string.history_detail_meta_credits, "$creditsUsedMinutes min")
        findViewById<TextView>(R.id.metaRequestId).text =
            getString(R.string.history_detail_meta_request, requestId.ifBlank { "N/A" })

        findViewById<Button>(R.id.copyButton).setOnClickListener {
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

    companion object {
        const val EXTRA_REQUEST_ID = "extra_request_id"
        const val EXTRA_TRANSCRIPT = "extra_transcript"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_CREDITS_USED_MINUTES = "extra_credits_used_minutes"
        const val EXTRA_CREATED_AT_MS = "extra_created_at_ms"
        const val EXTRA_CREATED_AT_LABEL = "extra_created_at_label"
    }
}
