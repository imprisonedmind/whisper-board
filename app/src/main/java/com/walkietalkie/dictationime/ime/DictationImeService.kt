package com.walkietalkie.dictationime.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.walkietalkie.dictationime.asr.WhisperApiRecognizer
import com.walkietalkie.dictationime.audio.AndroidAudioCapture
import com.walkietalkie.dictationime.audio.extractAudioFeatures
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import com.walkietalkie.dictationime.model.RemoteModelManager
import com.walkietalkie.dictationime.settings.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DictationImeService : InputMethodService(), CoroutineScope by MainScope() {
    private val logTag = "DictationImeService"

    private val modelManager by lazy { RemoteModelManager() }
    private val recognizer by lazy { WhisperApiRecognizer() }
    private val audioCapture by lazy { AndroidAudioCapture() }

    private lateinit var dictationController: DictationController
    private var keyboardView: KeyboardView? = null
    private var pendingSend = false
    private var lastAudioUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        dictationController = DictationController(
            audioCapture = audioCapture,
            speechRecognizer = recognizer,
            modelManager = modelManager,
            modelId = DEFAULT_MODEL_ID,
            onCommitText = { text ->
                Log.d(logTag, "commitFinalText length=${text.length}")
                commitFinalText(text)
                if (pendingSend) {
                    pendingSend = false
                    performSendAction()
                }
            },
            onStateChanged = { state ->
                keyboardView?.render(state)
                if (state is DictationState.Error) {
                    pendingSend = false
                    launch {
                        delay(1200)
                        dictationController.acknowledgeError()
                        keyboardView?.render(dictationController.state)
                    }
                }
            }
        )
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this).apply {
            onMicTap = { handleMicTap() }
            onOpenSettings = { openSettingsScreen() }
            onEraseTap = { performErase() }
            onSwitchKeyboard = { switchBackToKeyboard() }
            render(dictationController.state)
        }
        keyboardView = view
        return view
    }

    override fun onFinishInput() {
        super.onFinishInput()
        pendingSend = false
        dictationController.cancel()
        keyboardView?.render(dictationController.state)
    }

    override fun onDestroy() {
        super.onDestroy()
        runBlocking {
            dictationController.close()
        }
        cancel()
    }

    private fun handleMicTap() {
        launch {
            when (dictationController.state) {
                DictationState.Idle,
                is DictationState.Error -> {
                    Log.d(logTag, "mic tap -> start")
                    if (!hasRecordAudioPermission()) {
                        showError(DictationError.PermissionDenied)
                        return@launch
                    }
                    pendingSend = false
                    lastAudioUpdateMs = 0L
                    dictationController.startRecording { chunk ->
                        handleAudioChunk(chunk)
                    }
                }

                DictationState.Recording -> {
                    Log.d(logTag, "mic tap -> stop and transcribe")
                    pendingSend = true
                    dictationController.stopAndTranscribe()
                }

                DictationState.Transcribing -> {
                    Log.d(logTag, "mic tap ignored during transcribing")
                }
            }
        }
    }

    private fun showError(error: DictationError) {
        Log.w(logTag, "error state=$error")
        val state = DictationState.Error(error)
        keyboardView?.render(state)
        launch {
            delay(1200)
            keyboardView?.render(DictationState.Idle)
        }
    }

    private fun handleAudioChunk(chunk: ShortArray) {
        if (dictationController.state != DictationState.Recording) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAudioUpdateMs < 30L) return
        lastAudioUpdateMs = now
        val features = extractAudioFeatures(chunk)
        keyboardView?.post {
            keyboardView?.updateWaveform(features)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openSettingsScreen() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun performErase() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.deleteSurroundingText(1, 0)
    }

    private fun switchBackToKeyboard() {
        val switched = switchToPreviousInputMethod()
        if (!switched) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun performSendAction() {
        val inputConnection = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED

        val handledPrimary = when (action) {
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO -> inputConnection.performEditorAction(action)
            else -> false
        }

        if (handledPrimary) return

        // Many apps send on Enter; fall back to Enter key events first.
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))

        // If Enter does not send, try explicit send action as a last resort.
        inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
    }

    private fun commitFinalText(text: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(text, 1)
    }
}
