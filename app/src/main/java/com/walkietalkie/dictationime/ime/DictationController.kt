package com.walkietalkie.dictationime.ime

import com.walkietalkie.dictationime.asr.SpeechRecognizer
import com.walkietalkie.dictationime.audio.AudioCapture
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import com.walkietalkie.dictationime.model.TranscriptionModels
import com.walkietalkie.dictationime.model.ModelManager

class DictationController(
    private val audioCapture: AudioCapture,
    private val speechRecognizer: SpeechRecognizer,
    private val modelManager: ModelManager,
    private val modelIdProvider: () -> String = { DEFAULT_MODEL_ID },
    private val onCommitText: (String) -> Unit,
    private val onStateChanged: (DictationState) -> Unit = {}
) {
    var state: DictationState = DictationState.Idle
        private set

    private fun setState(next: DictationState) {
        state = next
        onStateChanged(next)
    }

    suspend fun startRecording(onAudioChunk: ((ShortArray) -> Unit)? = null): Result<Unit> {
        if (state != DictationState.Idle && state !is DictationState.Error) {
            return Result.failure(IllegalStateException("Cannot start in current state: $state"))
        }

        val modelId = TranscriptionModels.normalizeModelId(modelIdProvider())
        modelManager.ensureModelReady(modelId).onFailure {
            setState(DictationState.Error(DictationError.ConfigurationMissing))
            return Result.failure(it)
        }

        speechRecognizer.warmup(modelId).onFailure {
            setState(DictationState.Error(DictationError.ConfigurationMissing))
            return Result.failure(it)
        }

        audioCapture.start(onAudioChunk).onFailure {
            setState(DictationState.Error(DictationError.AudioCaptureFailed))
            return Result.failure(it)
        }

        setState(DictationState.Recording)
        return Result.success(Unit)
    }

    suspend fun stopAndTranscribe(): Result<Unit> {
        if (state != DictationState.Recording) {
            return Result.failure(IllegalStateException("Not recording"))
        }

        setState(DictationState.Transcribing)

        val pcm = audioCapture.stop().getOrElse {
            setState(DictationState.Error(DictationError.AudioCaptureFailed))
            return Result.failure(it)
        }

        if (pcm.size < MIN_SAMPLES_FOR_300MS) {
            clearBuffer(pcm)
            setState(DictationState.Error(DictationError.TooShort))
            return Result.success(Unit)
        }

        val transcription = speechRecognizer.transcribe(pcm)
        clearBuffer(pcm)

        return transcription.fold(
            onSuccess = { result ->
                if (result.text.isBlank()) {
                    setState(DictationState.Error(DictationError.NoSpeechDetected))
                } else {
                    onCommitText(result.text)
                    setState(DictationState.Idle)
                }
                Result.success(Unit)
            },
            onFailure = {
                setState(DictationState.Error(DictationError.TranscriptionFailed))
                Result.failure(it)
            }
        )
    }

    fun cancel() {
        audioCapture.cancel()
        setState(DictationState.Idle)
    }

    fun acknowledgeError() {
        if (state is DictationState.Error) {
            setState(DictationState.Idle)
        }
    }


    suspend fun close() {
        speechRecognizer.close()
    }

    private fun clearBuffer(buffer: ShortArray) {
        buffer.fill(0)
    }

    companion object {
        private const val MIN_SAMPLES_FOR_300MS = 4_800
    }
}
