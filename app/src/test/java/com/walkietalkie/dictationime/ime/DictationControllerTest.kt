package com.walkietalkie.dictationime.ime

import com.walkietalkie.dictationime.asr.SpeechRecognizer
import com.walkietalkie.dictationime.asr.TranscriptionResult
import com.walkietalkie.dictationime.audio.AudioCapture
import com.walkietalkie.dictationime.model.ModelInfo
import com.walkietalkie.dictationime.model.ModelManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationControllerTest {

    @Test
    fun `state transitions recording to transcription to idle`() = runTest {
        val fakeAudio = FakeAudioCapture(audio = ShortArray(7_000) { 12 })
        val fakeRecognizer = FakeSpeechRecognizer(
            transcription = TranscriptionResult("hello world", null, 120)
        )
        val fakeModelManager = FakeModelManager()
        val committed = mutableListOf<String>()
        val states = mutableListOf<DictationState>()

        val controller = DictationController(
            audioCapture = fakeAudio,
            speechRecognizer = fakeRecognizer,
            modelManager = fakeModelManager,
            modelId = "tiny.en-q5_1",
            onCommitText = { committed += it },
            onStateChanged = { states += it }
        )

        controller.startRecording()
        controller.stopAndTranscribe()

        assertEquals(listOf(DictationState.Recording, DictationState.Transcribing, DictationState.Idle), states)
        assertEquals(listOf("hello world"), committed)
    }

    @Test
    fun `maps transcribe failure to transcription error`() = runTest {
        val fakeAudio = FakeAudioCapture(audio = ShortArray(7_000) { 1 })
        val fakeRecognizer = FakeSpeechRecognizer(
            transcription = null,
            transcriptionError = IllegalStateException("native failure")
        )

        val controller = DictationController(
            audioCapture = fakeAudio,
            speechRecognizer = fakeRecognizer,
            modelManager = FakeModelManager(),
            modelId = "tiny.en-q5_1",
            onCommitText = {}
        )

        controller.startRecording()
        controller.stopAndTranscribe()

        assertEquals(DictationState.Error(DictationError.TranscriptionFailed), controller.state)
    }

    @Test
    fun `does not commit empty transcript`() = runTest {
        val fakeAudio = FakeAudioCapture(audio = ShortArray(7_000) { 1 })
        val fakeRecognizer = FakeSpeechRecognizer(
            transcription = TranscriptionResult("   ", null, 80)
        )
        val commits = mutableListOf<String>()

        val controller = DictationController(
            audioCapture = fakeAudio,
            speechRecognizer = fakeRecognizer,
            modelManager = FakeModelManager(),
            modelId = "tiny.en-q5_1",
            onCommitText = { commits += it }
        )

        controller.startRecording()
        controller.stopAndTranscribe()

        assertTrue(commits.isEmpty())
        assertEquals(DictationState.Error(DictationError.NoSpeechDetected), controller.state)
    }

    @Test
    fun `clears audio buffer after stop and supports cancel`() = runTest {
        val sharedBuffer = ShortArray(7_000) { 99 }
        val fakeAudio = FakeAudioCapture(audio = sharedBuffer)
        val fakeRecognizer = FakeSpeechRecognizer(
            transcription = TranscriptionResult("ok", null, 40)
        )

        val controller = DictationController(
            audioCapture = fakeAudio,
            speechRecognizer = fakeRecognizer,
            modelManager = FakeModelManager(),
            modelId = "tiny.en-q5_1",
            onCommitText = {}
        )

        controller.startRecording()
        controller.stopAndTranscribe()

        assertTrue(sharedBuffer.all { it == 0.toShort() })

        controller.cancel()
        assertTrue(fakeAudio.cancelCalled)
        assertFalse(controller.state is DictationState.Error)
        assertEquals(DictationState.Idle, controller.state)
    }

    private class FakeModelManager : ModelManager {
        override suspend fun ensureModelReady(modelId: String): Result<ModelInfo> {
            return Result.success(ModelInfo(modelId, "/tmp/$modelId.bin", 123L))
        }

        override fun currentModel(): ModelInfo {
            return ModelInfo("tiny.en-q5_1", "/tmp/tiny.en-q5_1.bin", 123L)
        }
    }

    private class FakeAudioCapture(private val audio: ShortArray) : AudioCapture {
        var cancelCalled = false

        override fun start(onAudioChunk: ((ShortArray) -> Unit)?): Result<Unit> {
            return Result.success(Unit)
        }

        override fun stop(): Result<ShortArray> = Result.success(audio)

        override fun cancel() {
            cancelCalled = true
        }
    }

    private class FakeSpeechRecognizer(
        private val transcription: TranscriptionResult?,
        private val transcriptionError: Throwable? = null
    ) : SpeechRecognizer {
        override suspend fun warmup(modelId: String): Result<Unit> = Result.success(Unit)

        override suspend fun transcribe(pcm16Mono16k: ShortArray): Result<TranscriptionResult> {
            transcriptionError?.let { return Result.failure(it) }
            return Result.success(requireNotNull(transcription))
        }

        override suspend fun close() = Unit
    }
}
