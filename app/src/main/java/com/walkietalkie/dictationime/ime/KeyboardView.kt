package com.walkietalkie.dictationime.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import com.walkietalkie.dictationime.R
import com.walkietalkie.dictationime.audio.AudioFeatures

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onMicTap: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onEraseTap: (() -> Unit)? = null
    var onSwitchKeyboard: (() -> Unit)? = null
    var onBuyCreditsTap: (() -> Unit)? = null

    private val typeface = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT

    private val colorSurface = Color.parseColor("#0A0F16")
    private val colorPanel = Color.parseColor("#111824")
    private val colorPanelAlt = Color.parseColor("#1B2636")
    private val colorKeyBorder = Color.parseColor("#304158")
    private val colorTextPrimary = Color.parseColor("#E8EEF7")
    private val colorTextMuted = Color.parseColor("#92A0B7")
    private val colorAccent = Color.parseColor("#24C4B3")
    private val colorRecording = Color.parseColor("#F0555D")
    private val colorTranscribing = Color.parseColor("#31C48D")

    private val waveformView = WaveformView(context)
    private val outOfCreditsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        background = roundedRectDrawable(radiusDp = 16, fillColor = colorPanel, strokeColor = colorKeyBorder)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        visibility = GONE
    }

    private val outOfCreditsTitle = TextView(context).apply {
        typeface = this@KeyboardView.typeface
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(colorTextPrimary)
        text = context.getString(R.string.out_of_credits_title)
    }

    private val outOfCreditsBody = TextView(context).apply {
        typeface = this@KeyboardView.typeface
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(colorTextMuted)
        gravity = Gravity.CENTER
        text = context.getString(R.string.out_of_credits_body)
    }

    private val outOfCreditsButton = AppCompatButton(context).apply {
        text = context.getString(R.string.out_of_credits_cta)
        typeface = this@KeyboardView.typeface
        textSize = 12f
        letterSpacing = 0.08f
        isAllCaps = true
        setTextColor(Color.parseColor("#041314"))
        background = roundedRectDrawable(radiusDp = 16, fillColor = colorAccent, strokeColor = colorAccent)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        setOnClickListener {
            performTapHaptic()
            onBuyCreditsTap?.invoke()
        }
    }

    private val waveformContainer = FrameLayout(context).apply {
        background = roundedRectDrawable(radiusDp = 12, fillColor = colorPanel, strokeColor = colorKeyBorder)
        alpha = 0f
        visibility = GONE
        addView(waveformView, LayoutParams(LayoutParams.MATCH_PARENT, dp(78)).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
            topMargin = dp(6)
            bottomMargin = dp(6)
        })
    }

    private val statusText = TextView(context).apply {
        typeface = this@KeyboardView.typeface
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.12f
        setTextColor(colorTextMuted)
        gravity = Gravity.CENTER
        text = context.getString(R.string.ime_tap_to_start)
        isAllCaps = true
    }

    private val micRing = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2), Color.parseColor("#3A4E67"))
        }
    }

    private val micIcon = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_mic_te)
        imageTintList = android.content.res.ColorStateList.valueOf(colorTextPrimary)
    }

    private val micButton = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colorPanelAlt)
            setStroke(dp(2), Color.parseColor("#3C4F67"))
        }
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.ime_mic)
        setOnClickListener {
            performTapHaptic()
            onMicTap?.invoke()
        }
        addView(micIcon, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
    }

    private val settingsButton = createCircleIconButton(
        iconRes = R.drawable.ic_settings,
        contentDescription = context.getString(R.string.ime_open_settings)
    ) { onOpenSettings?.invoke() }

    private val backspaceButton = createCircleIconButton(
        iconRes = R.drawable.ic_backspace,
        contentDescription = context.getString(R.string.ime_erase)
    ) { onEraseTap?.invoke() }

    private val keyboardSwitchButton = AppCompatButton(context).apply {
        text = context.getString(R.string.ime_keyboard_mode)
        typeface = this@KeyboardView.typeface
        textSize = 11f
        letterSpacing = 0.08f
        isAllCaps = true
        setTextColor(colorTextMuted)
        background = roundedRectDrawable(radiusDp = 18, fillColor = colorPanel, strokeColor = colorKeyBorder)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener {
            performTapHaptic()
            onSwitchKeyboard?.invoke()
        }
    }

    private val eraseRepeater = EraseRepeater()
    private var waveformMode = WaveformMode.Idle
    private var outOfCreditsMode = false
    private val controlsRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(colorSurface)
        setPadding(dp(12), dp(12), dp(12), dp(14))

        outOfCreditsContainer.addView(outOfCreditsTitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        outOfCreditsContainer.addView(outOfCreditsBody, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        outOfCreditsContainer.addView(outOfCreditsButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        addView(outOfCreditsContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(waveformContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(statusText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(14)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        val micStack = FrameLayout(context).apply {
            addView(micRing, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
            addView(micButton, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        }

        controlsRow.addView(settingsButton, LayoutParams(dp(50), dp(50)))
        controlsRow.addView(micStack, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(20)
            marginEnd = dp(20)
        })
        controlsRow.addView(backspaceButton, LayoutParams(dp(50), dp(50)))

        backspaceButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    performTapHaptic()
                    eraseRepeater.start()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    eraseRepeater.stop()
                    true
                }
                else -> false
            }
        }

        addView(controlsRow)

        addView(keyboardSwitchButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(14)
            bottomMargin = dp(14)
        })
    }

    fun render(state: DictationState) {
        if (outOfCreditsMode) {
            applyOutOfCreditsUi()
            return
        }
        when (state) {
            DictationState.Idle -> {
                setMicAppearance(
                    fillColor = colorPanelAlt,
                    borderColor = Color.parseColor("#3C4F67"),
                    ringColor = Color.parseColor("#39506B"),
                    iconRes = R.drawable.ic_mic_te,
                    iconTint = colorTextPrimary,
                    enableMic = true
                )
                statusText.text = context.getString(R.string.ime_tap_to_start)
                statusText.setTextColor(colorTextMuted)
                setWaveformState(visible = false, active = false, color = colorAccent, mode = WaveformMode.Idle)
            }

            DictationState.Recording -> {
                setMicAppearance(
                    fillColor = Color.parseColor("#A1303A"),
                    borderColor = Color.parseColor("#F46B72"),
                    ringColor = Color.parseColor("#F46B72"),
                    iconRes = R.drawable.ic_cancel_square,
                    iconTint = Color.WHITE,
                    enableMic = true
                )
                statusText.text = context.getString(R.string.ime_tap_to_stop)
                statusText.setTextColor(colorRecording)
                setWaveformState(visible = true, active = true, color = colorRecording, mode = WaveformMode.Reactive)
            }

            DictationState.Transcribing -> {
                setMicAppearance(
                    fillColor = Color.parseColor("#1D684D"),
                    borderColor = Color.parseColor("#46D89F"),
                    ringColor = Color.parseColor("#46D89F"),
                    iconRes = R.drawable.ic_waveform_te,
                    iconTint = Color.WHITE,
                    enableMic = false
                )
                statusText.text = context.getString(R.string.ime_transcribing)
                statusText.setTextColor(colorTranscribing)
                setWaveformState(visible = true, active = true, color = colorTranscribing, mode = WaveformMode.Ambient)
            }

            is DictationState.Error -> {
                setMicAppearance(
                    fillColor = colorPanelAlt,
                    borderColor = Color.parseColor("#3C4F67"),
                    ringColor = Color.parseColor("#39506B"),
                    iconRes = R.drawable.ic_mic_te,
                    iconTint = colorTextPrimary,
                    enableMic = true
                )
                statusText.text = errorToText(state.reason)
                statusText.setTextColor(colorRecording)
                setWaveformState(visible = false, active = false, color = colorAccent, mode = WaveformMode.Idle)
            }
        }
    }

    fun setOutOfCreditsMode(enabled: Boolean) {
        if (outOfCreditsMode == enabled) return
        outOfCreditsMode = enabled
        applyOutOfCreditsUi()
    }

    private fun applyOutOfCreditsUi() {
        if (outOfCreditsMode) {
            outOfCreditsContainer.visibility = VISIBLE
            statusText.visibility = GONE
            setWaveformState(visible = false, active = false, color = colorAccent, mode = WaveformMode.Idle)
            controlsRow.visibility = GONE
        } else {
            outOfCreditsContainer.visibility = GONE
            statusText.visibility = VISIBLE
            controlsRow.visibility = VISIBLE
            render(DictationState.Idle)
        }
    }

    private fun setMicAppearance(
        fillColor: Int,
        borderColor: Int,
        ringColor: Int,
        iconRes: Int,
        iconTint: Int,
        enableMic: Boolean
    ) {
        (micButton.background as? GradientDrawable)?.apply {
            setColor(fillColor)
            setStroke(dp(2), borderColor)
            invalidateSelf()
        }

        (micRing.background as? GradientDrawable)?.apply {
            setStroke(dp(2), ringColor)
            invalidateSelf()
        }

        micIcon.setImageResource(iconRes)
        micIcon.imageTintList = android.content.res.ColorStateList.valueOf(iconTint)
        micButton.isEnabled = enableMic
        micButton.alpha = if (enableMic) 1f else 0.75f
    }

    fun updateWaveform(features: AudioFeatures) {
        if (waveformMode != WaveformMode.Reactive) return
        waveformView.setAudioFeatures(features.rms, features.zcr)
    }

    private fun setWaveformState(
        visible: Boolean,
        active: Boolean,
        color: Int,
        mode: WaveformMode
    ) {
        waveformMode = mode
        waveformView.waveColor = color
        waveformView.isActive = active
        if (visible) {
            waveformView.ensureAnimating()
        }
        when (mode) {
            WaveformMode.Idle -> waveformView.setIdleMode()
            WaveformMode.Reactive -> waveformView.setReactiveMode()
            WaveformMode.Ambient -> waveformView.setAmbientMode()
        }

        if (visible) {
            if (waveformContainer.visibility != VISIBLE) {
                waveformContainer.visibility = VISIBLE
                waveformContainer.animate().cancel()
                waveformContainer.alpha = 0f
                waveformContainer.animate().alpha(1f).setDuration(180L).start()
            }
        } else if (waveformContainer.visibility == VISIBLE) {
            waveformContainer.animate().cancel()
            waveformContainer.animate()
                .alpha(0f)
                .setDuration(140L)
                .withEndAction {
                    waveformContainer.visibility = GONE
                }
                .start()
        }
    }

    private fun createCircleIconButton(
        iconRes: Int,
        contentDescription: String,
        onClick: () -> Unit
    ): AppCompatImageButton {
        return AppCompatImageButton(context).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(colorTextMuted)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            this.contentDescription = contentDescription
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPanelAlt)
                setStroke(dp(1), colorKeyBorder)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                performTapHaptic()
                onClick()
            }
        }
    }

    private fun errorToText(reason: DictationError): String {
        return when (reason) {
            DictationError.PermissionDenied -> context.getString(R.string.ime_permission_denied)
            DictationError.ConfigurationMissing -> context.getString(R.string.ime_configuration_missing)
            DictationError.AudioCaptureFailed -> context.getString(R.string.ime_unknown_error)
            DictationError.TranscriptionFailed -> context.getString(R.string.ime_transcription_failed)
            DictationError.TooShort -> context.getString(R.string.ime_too_short)
            DictationError.NoSpeechDetected -> context.getString(R.string.ime_no_speech)
            DictationError.Unknown -> context.getString(R.string.ime_unknown_error)
        }
    }

    private fun roundedRectDrawable(radiusDp: Int, fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
    }

    private fun performTapHaptic() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        eraseRepeater.stop()
    }

    private enum class WaveformMode {
        Idle,
        Reactive,
        Ambient
    }

    private inner class EraseRepeater {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val repeatDelayMs = 350L
        private val repeatIntervalMs = 70L
        private var active = false

        private val repeatRunnable = object : Runnable {
            override fun run() {
                if (!active) return
                onEraseTap?.invoke()
                handler.postDelayed(this, repeatIntervalMs)
            }
        }

        fun start() {
            if (active) return
            active = true
            onEraseTap?.invoke()
            handler.postDelayed(repeatRunnable, repeatDelayMs)
        }

        fun stop() {
            active = false
            handler.removeCallbacks(repeatRunnable)
        }
    }
}
