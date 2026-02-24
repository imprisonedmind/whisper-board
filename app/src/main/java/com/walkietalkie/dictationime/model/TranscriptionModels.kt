package com.walkietalkie.dictationime.model

import java.util.Locale

data class TranscriptionModelOption(
    val id: String,
    val label: String,
    val subtitle: String
)

object TranscriptionModels {
    val options: List<TranscriptionModelOption> = listOf(
        TranscriptionModelOption(
            id = "whisper-1",
            label = "Whisper-1",
            subtitle = "Consistent accuracy with stable formatting"
        ),
        TranscriptionModelOption(
            id = "gpt-4o-mini-transcribe",
            label = "GPT-4o Mini Transcribe",
            subtitle = "Fast response with strong style control"
        ),
        TranscriptionModelOption(
            id = "gpt-4o-transcribe",
            label = "GPT-4o Transcribe",
            subtitle = "Best overall quality for general dictation"
        ),
        TranscriptionModelOption(
            id = "gpt-4o-transcribe-diarize",
            label = "GPT-4o Transcribe Diarize",
            subtitle = "Tracks speakers in multi-person recordings"
        )
    )

    fun normalizeModelId(value: String?): String {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isBlank()) return DEFAULT_MODEL_ID
        return if (options.any { it.id.equals(cleaned, ignoreCase = true) }) {
            options.first { it.id.equals(cleaned, ignoreCase = true) }.id
        } else {
            DEFAULT_MODEL_ID
        }
    }

    fun displayLabelFor(modelId: String?): String {
        val normalized = normalizeModelId(modelId).lowercase(Locale.US)
        return options.firstOrNull { it.id.lowercase(Locale.US) == normalized }?.label ?: "Whisper-1"
    }
}
