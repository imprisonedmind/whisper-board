package com.walkietalkie.dictationime.asr

data class TranscriptionResult(
    val text: String,
    val confidence: Float?,
    val latencyMs: Long
)

interface SpeechRecognizer {
    suspend fun warmup(modelId: String): Result<Unit>
    suspend fun transcribe(pcm16Mono16k: ShortArray): Result<TranscriptionResult>
    suspend fun close()
}
