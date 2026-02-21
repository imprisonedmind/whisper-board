package com.walkietalkie.dictationime.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

interface AudioCapture {
    fun start(onAudioChunk: ((ShortArray) -> Unit)? = null): Result<Unit>
    fun stop(): Result<ShortArray>
    fun cancel()
}

class AndroidAudioCapture : AudioCapture {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRateHz = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var captureJob: Job? = null
    private val isCapturing = AtomicBoolean(false)
    private val lock = Any()
    private var pcmBuffer = ShortPcmBuffer()

    override fun start(onAudioChunk: ((ShortArray) -> Unit)?): Result<Unit> {
        return runCatching {
            if (!isCapturing.compareAndSet(false, true)) {
                throw IllegalStateException("Audio capture already running")
            }

            val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
            if (minBuffer <= 0) {
                isCapturing.set(false)
                throw IllegalStateException("Unable to compute audio buffer size")
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateHz,
                channelConfig,
                audioFormat,
                minBuffer * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isCapturing.set(false)
                throw IllegalStateException("AudioRecord failed to initialize")
            }

            synchronized(lock) {
                pcmBuffer = ShortPcmBuffer()
            }

            recorder.startRecording()
            audioRecord = recorder

            captureJob = scope.launch {
                val chunk = ShortArray(1024)
                while (isCapturing.get()) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        synchronized(lock) {
                            pcmBuffer.append(chunk, read)
                        }
                        onAudioChunk?.let { callback ->
                            // Copy to isolate shared read buffer from callback consumers.
                            runCatching { callback(chunk.copyOf(read)) }
                        }
                    }
                }
            }
        }
    }

    override fun stop(): Result<ShortArray> {
        return runCatching {
            if (!isCapturing.compareAndSet(true, false)) {
                throw IllegalStateException("Audio capture is not running")
            }

            // Unblock the read loop before joining it.
            val recorder = audioRecord
            audioRecord = null
            recorder?.let {
                runCatching { it.stop() }
            }

            runBlocking {
                captureJob?.join()
            }
            captureJob = null

            recorder?.release()

            synchronized(lock) {
                pcmBuffer.toShortArray()
            }
        }
    }

    override fun cancel() {
        if (isCapturing.compareAndSet(true, false)) {
            val recorder = audioRecord
            audioRecord = null
            recorder?.let {
                runCatching { it.stop() }
            }

            runBlocking {
                captureJob?.join()
            }
            captureJob = null
            recorder?.release()
        }
        synchronized(lock) {
            pcmBuffer.clear()
        }
    }
}

private class ShortPcmBuffer {
    private var data = ShortArray(16_384)
    private var size = 0

    fun append(chunk: ShortArray, count: Int) {
        ensureCapacity(size + count)
        chunk.copyInto(data, destinationOffset = size, startIndex = 0, endIndex = count)
        size += count
    }

    fun toShortArray(): ShortArray = data.copyOf(size)

    fun clear() {
        data.fill(0)
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        var newSize = data.size
        while (newSize < required) {
            newSize *= 2
        }
        data = data.copyOf(newSize)
    }
}
