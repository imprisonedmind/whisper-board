package com.walkietalkie.dictationime.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var isActive: Boolean = false
        set(value) {
            field = value
            if (!value) {
                setIdleMode()
            }
            invalidate()
        }

    var intensity: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.05f, 1.6f)
            targetIntensity = field
            invalidate()
        }

    var waveColor: Int = Color.parseColor("#24C4B3")
        set(value) {
            field = value
            updatePaintColors()
            invalidate()
        }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }

    private val tertiaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var phase = 0f
    private var targetIntensity = intensity
    private var currentIntensity = intensity
    private var targetSpeed = 0.05f
    private var currentSpeed = 0.05f
    private var targetTightness = 1f
    private var currentTightness = 1f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            val smoothing = if (isActive) 0.22f else 0.12f
            currentIntensity = approach(currentIntensity, targetIntensity, smoothing)
            currentSpeed = approach(currentSpeed, targetSpeed, smoothing)
            currentTightness = approach(currentTightness, targetTightness, smoothing)
            phase += currentSpeed
            invalidate()
        }
    }

    init {
        updatePaintColors()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    fun ensureAnimating() {
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val midY = height * 0.5f
        if (!isActive) {
            drawSine(canvas, mainPaint, midY, 3f * currentIntensity, 0.018f)
            return
        }

        val amplitude = (height * 0.33f) * currentIntensity
        drawSine(canvas, mainPaint, midY, amplitude, 0.018f)
        drawSine(canvas, secondaryPaint, midY, amplitude * 0.72f, 0.028f)
        drawSine(canvas, tertiaryPaint, midY, amplitude * 0.95f, 0.013f)

        val barCount = 34
        val barWidth = width / (barCount * 1.8f)
        val totalBarWidth = barCount * barWidth
        val gap = ((width - totalBarWidth) / (barCount + 1)).coerceAtLeast(1f)

        for (i in 0 until barCount) {
            val envelope = sin((i.toFloat() / barCount) * PI).toFloat().coerceAtLeast(0f)
            val wobblePhase = (i * 0.55f * currentTightness) + (phase * 2f)
            val wobble = (sin(wobblePhase) * 0.5f + 0.5f).toFloat()
            val barHeight = (height * 0.06f) + (height * 0.30f * wobble * envelope * currentIntensity)
            val left = gap + i * (barWidth + gap)
            val top = midY - barHeight / 2f
            val right = left + barWidth
            val bottom = midY + barHeight / 2f
            canvas.drawRoundRect(left, top, right, bottom, dp(1.2f), dp(1.2f), barPaint)
        }
    }

    private fun drawSine(canvas: Canvas, paint: Paint, midY: Float, amplitude: Float, frequency: Float) {
        val path = android.graphics.Path()
        var x = 0f
        val effectiveFrequency = frequency * currentTightness
        while (x <= width.toFloat()) {
            val y = midY + sin((x * effectiveFrequency) + phase).toFloat() * amplitude * envelope(x, width.toFloat())
            if (x == 0f) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            x += 2f
        }
        canvas.drawPath(path, paint)
    }

    private fun envelope(x: Float, width: Float): Float {
        val ratio = if (width == 0f) 0f else (x / width).coerceIn(0f, 1f)
        return sin(ratio * PI).toFloat().coerceAtLeast(0.15f)
    }

    private fun updatePaintColors() {
        mainPaint.color = applyAlpha(waveColor, 0.86f)
        secondaryPaint.color = applyAlpha(waveColor, 0.45f)
        tertiaryPaint.color = applyAlpha(waveColor, 0.25f)
        barPaint.color = applyAlpha(waveColor, 0.28f)
    }

    fun setAudioFeatures(rms: Float, zcr: Float) {
        val rmsBoosted = (rms * 30.0f).coerceIn(0f, 1f)
        val zcrBoosted = (zcr * 8.5f).coerceIn(0f, 1f)
        val energy = (rmsBoosted * 0.9f + zcrBoosted * 0.1f).coerceIn(0f, 1f)

        targetIntensity = lerp(0.28f, 1.5f, energy)
        val pace = (energy * 0.7f + zcrBoosted * 0.3f).coerceIn(0f, 1f)
        targetSpeed = lerp(0.06f, 0.3f, pace)
        targetTightness = lerp(0.75f, 1.6f, zcrBoosted)

        if (rms < 0.002f) {
            targetIntensity = 0.22f
            targetSpeed = 0.05f
            targetTightness = 0.9f
        }
    }

    fun setReactiveMode() {
        targetIntensity = 0.22f
        targetSpeed = 0.05f
        targetTightness = 1.0f
    }

    fun setAmbientMode() {
        targetIntensity = 0.35f
        targetSpeed = 0.07f
        targetTightness = 1.1f
    }

    fun setIdleMode() {
        targetIntensity = 0.18f
        targetSpeed = 0.02f
        targetTightness = 0.9f
    }

    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun lerp(start: Float, end: Float, t: Float): Float =
        start + (end - start) * t

    private fun approach(current: Float, target: Float, factor: Float): Float =
        current + (target - current) * factor

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
