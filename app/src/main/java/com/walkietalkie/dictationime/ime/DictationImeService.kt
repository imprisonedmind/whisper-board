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
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.walkietalkie.dictationime.asr.WhisperApiRecognizer
import com.walkietalkie.dictationime.audio.AndroidAudioCapture
import com.walkietalkie.dictationime.audio.extractAudioFeatures
import com.walkietalkie.dictationime.auth.AuthStore
import com.walkietalkie.dictationime.auth.LoginEmailActivity
import com.walkietalkie.dictationime.model.DEFAULT_MODEL_ID
import com.walkietalkie.dictationime.model.RemoteModelManager
import com.walkietalkie.dictationime.openai.OpenAiConfig
import com.walkietalkie.dictationime.settings.CreditStoreActivity
import com.walkietalkie.dictationime.settings.CreditStoreDataCache
import com.walkietalkie.dictationime.settings.MainActivity
import com.walkietalkie.dictationime.settings.AppProfilesStore
import com.walkietalkie.dictationime.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DictationImeService : InputMethodService(), CoroutineScope by MainScope() {
    private val logTag = "DictationImeService"

    private val modelManager by lazy { RemoteModelManager() }
    private val recognizer by lazy { WhisperApiRecognizer(this) }
    private val audioCapture by lazy { AndroidAudioCapture() }

    private lateinit var dictationController: DictationController
    private var keyboardView: KeyboardView? = null
    private var pendingSend = false
    private var lastAudioUpdateMs = 0L
    private val lastProfileTouchMs = mutableMapOf<String, Long>()
    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (SettingsStore.isOutOfCreditsKey(key)) {
                applyOutOfCreditsMode()
            }
            if (SettingsStore.isOpenSourceKey(key)) {
                applyOpenSourceMode()
            }
        }
    private val authListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            applyAuthMode()
        }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.registerListener(this, prefsListener)
        AuthStore.registerListener(this, authListener)
        applyOpenSourceMode()
        dictationController = DictationController(
            audioCapture = audioCapture,
            speechRecognizer = recognizer,
            modelManager = modelManager,
            modelIdProvider = { SettingsStore.getSelectedModel(this) },
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
                updateKeepScreenOn(state == DictationState.Recording)
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
            onCancelTap = { handleCancelTap() }
            onSwitchKeyboard = { switchBackToKeyboard() }
            onBuyCreditsTap = { openCreditStoreScreen() }
            onLoginTap = { openLoginScreen() }
            render(dictationController.state)
        }
        keyboardView = view
        applyAuthMode()
        applyOutOfCreditsMode()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        trackHostAppForProfiles(info)
        applyAuthMode()
        applyOutOfCreditsMode()
        applyOpenSourceMode()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        pendingSend = false
        dictationController.cancel()
        keyboardView?.render(dictationController.state)
        updateKeepScreenOn(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        SettingsStore.unregisterListener(this, prefsListener)
        AuthStore.unregisterListener(this, authListener)
        runBlocking {
            dictationController.close()
        }
        updateKeepScreenOn(false)
        cancel()
    }

    private fun applyOutOfCreditsMode() {
        val enabled = SettingsStore.isOutOfCreditsMode(this)
        keyboardView?.setOutOfCreditsMode(enabled)
        if (enabled) {
            pendingSend = false
            dictationController.cancel()
            updateKeepScreenOn(false)
        }
    }

    private fun applyOpenSourceMode() {
        OpenAiConfig.setOpenSourceOverride(SettingsStore.isOpenSourceMode(this))
    }

    private fun applyAuthMode() {
        val isSignedIn = AuthStore.isSignedIn(this)
        keyboardView?.setLoggedOutMode(!isSignedIn)
    }

    private fun trackHostAppForProfiles(info: EditorInfo?) {
        val packageName = info?.packageName?.trim().orEmpty()
        if (packageName.isBlank() || packageName == this.packageName) {
            recognizer.currentAppPackage = null
            val resolved = resolvePromptForPackage(null)
            recognizer.currentPrompt = resolved.prompt
            recognizer.currentProfileKey = resolved.profileKey
            return
        }
        recognizer.currentAppPackage = packageName
        val resolved = resolvePromptForPackage(packageName)
        recognizer.currentPrompt = resolved.prompt
        recognizer.currentProfileKey = resolved.profileKey
        val now = System.currentTimeMillis()
        val previousTouch = lastProfileTouchMs[packageName] ?: 0L
        if (now - previousTouch < 30_000L) {
            return
        }
        lastProfileTouchMs[packageName] = now

        // Cache first so profile list updates immediately even if sync fails.
        AppProfilesStore.cacheKnownAppsLocal(this, listOf(packageName))
        launch {
            runCatching {
                AppProfilesStore.touchAppProfile(this@DictationImeService, packageName, now)
            }
        }
    }

    private data class ResolvedProfilePrompt(
        val prompt: String?,
        val profileKey: String?
    )

    private fun resolvePromptForPackage(packageName: String?): ResolvedProfilePrompt {
        val profiles = AppProfilesStore.getCachedProfiles(this)
        val appPrompt = packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { profiles.appPrompts[it] }
            ?.trim()
            .orEmpty()
        if (appPrompt.isNotBlank()) {
            return ResolvedProfilePrompt(
                prompt = appPrompt,
                profileKey = packageName
            )
        }
        val defaultPrompt = profiles.defaultPrompt.trim()
        if (defaultPrompt.isNotBlank()) {
            return ResolvedProfilePrompt(
                prompt = defaultPrompt,
                profileKey = "default"
            )
        }
        return ResolvedProfilePrompt(prompt = null, profileKey = null)
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

    private fun handleCancelTap() {
        if (dictationController.state != DictationState.Recording) return
        pendingSend = false
        dictationController.cancel()
        keyboardView?.render(dictationController.state)
        updateKeepScreenOn(false)
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

    private fun updateKeepScreenOn(keepOn: Boolean) {
        keyboardView?.keepScreenOn = keepOn
        window?.window?.let { imeWindow ->
            if (keepOn) {
                imeWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                imeWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
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

    private fun openLoginScreen() {
        val intent = Intent(this, LoginEmailActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openCreditStoreScreen() {
        launch {
            CreditStoreDataCache.prefetch(this@DictationImeService)
        }
        val intent = Intent(this, CreditStoreActivity::class.java)
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
