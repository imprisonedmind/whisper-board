package com.walkietalkie.dictationime.asr

import com.walkietalkie.dictationime.openai.OpenAiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class WhisperApiRecognizer(
    private val config: OpenAiConfig = OpenAiConfig,
    private val client: OkHttpClient = defaultClient()
) : SpeechRecognizer {

    private val lock = Mutex()
    private var activeModelId: String? = null

    override suspend fun warmup(modelId: String): Result<Unit> {
        return lock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureConfigured()
                    activeModelId = modelId
                }
            }
        }
    }

    override suspend fun transcribe(pcm16Mono16k: ShortArray): Result<TranscriptionResult> {
        return lock.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    ensureConfigured()
                    val modelId = activeModelId ?: throw IllegalStateException("Recognizer is not initialized")
                    val wavBytes = pcm16ToWavBytes(pcm16Mono16k, sampleRateHz = 16_000, channels = 1)

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("model", modelId)
                        .addFormDataPart(
                            "file",
                            "audio.wav",
                            wavBytes.toRequestBody("audio/wav".toMediaType())
                        )
                        .build()

                    var responsePayload: String? = null
                    val elapsed = measureTimeMillis {
                        val request = Request.Builder()
                            .url("${config.baseUrl}/audio/transcriptions")
                            .header("Authorization", "Bearer ${config.apiKey}")
                            .post(requestBody)
                            .build()

                        client.newCall(request).execute().use { response ->
                            responsePayload = response.body?.string()
                            if (!response.isSuccessful) {
                                throw IOException(
                                    "OpenAI transcription failed: HTTP ${response.code} " +
                                        (responsePayload?.take(200) ?: "")
                                )
                            }
                        }
                    }

                    val body = responsePayload ?: throw IOException("Empty response from OpenAI")
                    val text = JSONObject(body).optString("text", "")

                    TranscriptionResult(
                        text = text.trim(),
                        confidence = null,
                        latencyMs = elapsed
                    )
                }
            }
        }
    }

    override suspend fun close() = Unit

    private fun ensureConfigured() {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("OpenAI API key missing")
        }
    }

    private fun pcm16ToWavBytes(
        pcm16Mono: ShortArray,
        sampleRateHz: Int,
        channels: Int
    ): ByteArray {
        val dataSize = pcm16Mono.size * 2
        val totalSize = 36 + dataSize
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(sampleRateHz * channels * 2)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        for (sample in pcm16Mono) {
            buffer.putShort(sample)
        }

        return buffer.array()
    }

    companion object {
        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
