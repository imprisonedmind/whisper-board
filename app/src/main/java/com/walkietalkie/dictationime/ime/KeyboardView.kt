package com.walkietalkie.dictationime.ime

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.walkietalkie.dictationime.R
import kotlin.math.min

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onMicTap: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onEraseTap: (() -> Unit)? = null
    var onBackToKeyboard: (() -> Unit)? = null

    private val surfaceColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorSurface,
        ContextCompat.getColor(context, R.color.surface)
    )
    private val surfaceVariantColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorSurfaceVariant,
        ContextCompat.getColor(context, R.color.surface_alt)
    )
    private val onSurfaceColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorOnSurface,
        ContextCompat.getColor(context, R.color.ink)
    )
    private val primaryColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorPrimary,
        ContextCompat.getColor(context, R.color.accent)
    )
    private val onPrimaryColor = MaterialColors.getColor(
        this,
        com.google.android.material.R.attr.colorOnPrimary,
        ContextCompat.getColor(context, R.color.on_primary)
    )
    private val primaryPressedColor = ContextCompat.getColor(context, R.color.accent_pressed)
    private val rippleColor = MaterialColors.getColor(
        this,
        android.R.attr.colorControlHighlight,
        onSurfaceColor
    )

    private val topButtonSize = dp(40)
    private val topButtonPadding = dp(10)
    private val actionButtonSize = dp(88)
    private val haloSize = dp(132)

    private val statusText = TextView(context).apply {
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(onSurfaceColor)
        gravity = Gravity.CENTER
        text = ""
        visibility = INVISIBLE
    }

    private val actionFill = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = (actionButtonSize / 2).toFloat()
        setColor(primaryColor)
    }

    private val actionButton = AppCompatImageButton(context).apply {
        background = RippleDrawable(ColorStateList.valueOf(rippleColor), actionFill, null)
        setImageResource(android.R.drawable.ic_btn_speak_now)
        imageTintList = ColorStateList.valueOf(onPrimaryColor)
        imageAlpha = 255
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(12), dp(12), dp(12), dp(12))
        layoutParams = LayoutParams(actionButtonSize, actionButtonSize)
        contentDescription = context.getString(R.string.ime_mic)
        setOnClickListener { onMicTap?.invoke() }
        elevation = dp(6).toFloat()
    }

    private var haloColor: Int = primaryColor
    private val haloDrawable = ShapeDrawable(OvalShape()).apply {
        paint.isDither = true
        shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                val radius = min(width, height) * 0.5f
                val inner = ColorUtils.setAlphaComponent(haloColor, 140)
                val outer = ColorUtils.setAlphaComponent(haloColor, 0)
                return RadialGradient(
                    width * 0.5f,
                    height * 0.5f,
                    radius,
                    intArrayOf(inner, outer),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    private val actionHalo = View(context).apply {
        background = haloDrawable
        alpha = 0f
        visibility = INVISIBLE
        elevation = 0f
        isClickable = false
        isFocusable = false
    }

    private var haloAnimator: ObjectAnimator? = null

    private val eraseRepeater = EraseRepeater()

    private val backButton = makeIconButton(
        iconRes = R.drawable.ic_back,
        contentDescriptionRes = R.string.ime_back_to_keyboard,
        fillColor = surfaceVariantColor,
        iconTint = onSurfaceColor
    ) { onBackToKeyboard?.invoke() }

    private val settingsButton = makeIconButton(
        iconRes = R.drawable.ic_settings,
        contentDescriptionRes = R.string.ime_open_settings,
        fillColor = surfaceVariantColor,
        iconTint = onSurfaceColor
    ) { onOpenSettings?.invoke() }

    private val eraseButton = makeIconButton(
        iconRes = R.drawable.ic_backspace,
        contentDescriptionRes = R.string.ime_erase,
        fillColor = surfaceVariantColor,
        iconTint = onSurfaceColor
    ) { onEraseTap?.invoke() }.apply {
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
    }

    init {
        orientation = VERTICAL
        clipToPadding = false
        clipChildren = false
        setBackgroundColor(surfaceColor)
        setPadding(dp(16), dp(18), dp(16), dp(32))

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton)
            addView(LinearLayout(context).apply {
                layoutParams = LayoutParams(0, 0, 1f)
            })
            addView(settingsButton)
            addView(eraseButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(10)
            })
        }

        val actionContainer = FrameLayout(context).apply {
            clipToPadding = false
            clipChildren = false
            addView(
                actionHalo,
                FrameLayout.LayoutParams(haloSize, haloSize, Gravity.CENTER)
            )
            addView(
                actionButton,
                FrameLayout.LayoutParams(actionButtonSize, actionButtonSize, Gravity.CENTER)
            )
            actionHalo.translationZ = 0f
            actionButton.translationZ = dp(6).toFloat()
        }

        val centerRow = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(actionContainer)
            addView(statusText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }

        val centerContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            clipToPadding = false
            clipChildren = false
            addView(centerRow)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        addView(
            topRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(32)
            }
        )
        addView(centerContainer)
    }

    fun render(state: DictationState) {
        when (state) {
            DictationState.Idle -> {
                actionButton.isEnabled = true
                statusText.text = ""
                statusText.visibility = INVISIBLE
                actionFill.setColor(primaryColor)
                actionButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                actionButton.imageTintList = ColorStateList.valueOf(surfaceColor)
                actionButton.contentDescription = context.getString(R.string.ime_mic)
                stopHalo()
            }

            DictationState.Recording -> {
                actionButton.isEnabled = true
                statusText.text = context.getString(R.string.ime_recording)
                statusText.visibility = VISIBLE
                actionFill.setColor(primaryPressedColor)
                actionButton.setImageResource(R.drawable.ic_send)
                actionButton.imageTintList = ColorStateList.valueOf(surfaceColor)
                actionButton.contentDescription = context.getString(R.string.ime_tap_to_send)
                startHalo(durationMs = 1800L, color = primaryPressedColor, maxAlpha = 0.22f)
            }

            DictationState.Transcribing -> {
                actionButton.isEnabled = false
                statusText.text = context.getString(R.string.ime_transcribing_send)
                statusText.visibility = VISIBLE
                actionFill.setColor(surfaceVariantColor)
                actionButton.setImageResource(R.drawable.ic_send)
                actionButton.imageTintList = ColorStateList.valueOf(surfaceColor)
                actionButton.contentDescription = context.getString(R.string.ime_transcribing_send)
                startHalo(durationMs = 900L, color = primaryColor, maxAlpha = 0.18f)
            }

            is DictationState.Error -> {
                actionButton.isEnabled = true
                actionFill.setColor(primaryColor)
                actionButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                actionButton.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                actionButton.contentDescription = context.getString(R.string.ime_mic)
                statusText.text = when (state.reason) {
                    DictationError.PermissionDenied -> context.getString(R.string.ime_permission_denied)
                    DictationError.ConfigurationMissing -> context.getString(R.string.ime_configuration_missing)
                    DictationError.AudioCaptureFailed -> context.getString(R.string.ime_unknown_error)
                    DictationError.TranscriptionFailed -> context.getString(R.string.ime_transcription_failed)
                    DictationError.TooShort -> context.getString(R.string.ime_too_short)
                    DictationError.NoSpeechDetected -> context.getString(R.string.ime_no_speech)
                    DictationError.Unknown -> context.getString(R.string.ime_unknown_error)
                }
                statusText.visibility = VISIBLE
                stopHalo()
            }
        }

        actionButton.alpha = if (actionButton.isEnabled) 1f else 0.6f
    }

    private fun makeIconButton(
        iconRes: Int,
        contentDescriptionRes: Int,
        fillColor: Int,
        iconTint: Int,
        onClick: () -> Unit
    ): AppCompatImageButton {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }

        return AppCompatImageButton(context).apply {
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), shape, null)
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconTint)
            imageAlpha = 255
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(topButtonPadding, topButtonPadding, topButtonPadding, topButtonPadding)
            layoutParams = LayoutParams(topButtonSize, topButtonSize)
            contentDescription = context.getString(contentDescriptionRes)
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun startHalo(durationMs: Long, color: Int, maxAlpha: Float) {
        haloAnimator?.cancel()
        haloColor = color
        haloDrawable.invalidateSelf()
        actionHalo.visibility = VISIBLE
        actionHalo.scaleX = 0.9f
        actionHalo.scaleY = 0.9f
        actionHalo.alpha = maxAlpha * 0.6f
        haloAnimator = ObjectAnimator.ofPropertyValuesHolder(
            actionHalo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.12f),
            PropertyValuesHolder.ofFloat(View.ALPHA, maxAlpha * 0.6f, maxAlpha)
        ).apply {
            duration = durationMs
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopHalo() {
        haloAnimator?.cancel()
        haloAnimator = null
        actionHalo.visibility = INVISIBLE
        actionHalo.alpha = 0f
        actionHalo.scaleX = 1f
        actionHalo.scaleY = 1f
    }



    private inner class EraseRepeater {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private val repeatDelayMs = 350L
        private val repeatIntervalMs = 60L
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
