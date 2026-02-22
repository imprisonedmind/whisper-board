package com.walkietalkie.dictationime.ime

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
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

    var waveColor: Int = Color.parseColor("#24C4B3")
        set(value) {
            field = value
            updatePaintColors()
            invalidate()
        }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val waveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val historySize = 72
    private val levelHistory = FloatArray(historySize) { 0.02f }
    private var writeIndex = 0
    private var levelPhase = 0f
    private var renderMode = RenderMode.Idle
    private var targetLevel = 0.03f
    private var currentLevel = 0.03f
    private var ambientBase = 0.12f
    private var lastPushAt = 0L

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            levelPhase += 0.11f
            currentLevel = approach(currentLevel, targetLevel, if (isActive) 0.30f else 0.16f)
            tickHistory()
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
        canvas.drawLine(0f, midY, width, midY, baselinePaint)

        val barWidth = dp(4f)
        val gap = dp(3f)
        val slot = barWidth + gap
        val barCount = max(1, (width / slot).toInt())
        val startX = width - (barCount * slot) + gap
        val minHalfHeight = dp(1f)
        val maxHalfHeight = height * 0.38f

        for (i in 0 until barCount) {
            val x = startX + (i * slot)
            val historyIndex = ((writeIndex - (barCount - i) + historySize) % historySize)
            val normalized = levelHistory[historyIndex].coerceIn(0f, 1f)
            val halfHeight = minHalfHeight + normalized * maxHalfHeight
            canvas.drawRoundRect(
                x,
                midY - halfHeight,
                x + barWidth,
                midY + halfHeight,
                barWidth * 0.5f,
                barWidth * 0.5f,
                waveBarPaint
            )
        }
    }

    private fun tickHistory() {
        val now = System.currentTimeMillis()
        if (now - lastPushAt < 24L) return
        lastPushAt = now

        val next = when (renderMode) {
            RenderMode.Reactive -> {
                targetLevel *= 0.965f
                currentLevel
            }
            RenderMode.Ambient -> {
                ambientBase + ((sin(levelPhase.toDouble()).toFloat() * 0.5f + 0.5f) * 0.09f)
            }
            RenderMode.Idle -> 0.02f
        }.coerceIn(0f, 1f)

        levelHistory[writeIndex] = next
        writeIndex = (writeIndex + 1) % historySize
    }

    private fun updatePaintColors() {
        baselinePaint.color = applyAlpha(waveColor, 0.28f)
        waveBarPaint.color = applyAlpha(waveColor, 0.95f)
    }

    fun setAudioFeatures(rms: Float, zcr: Float) {
        if (renderMode != RenderMode.Reactive) return
        val rmsEnergy = (rms * 60f).coerceIn(0f, 1f)
        val zcrEnergy = (zcr * 8.5f).coerceIn(0f, 1f)
        val combined = (rmsEnergy * 0.95f + zcrEnergy * 0.05f).coerceIn(0f, 1f)

        val floor = if (rms < 0.0012f) 0.03f else 0.085f
        val candidate = lerp(floor, 1f, combined)
        val attack = if (candidate > targetLevel) 0.86f else 0.30f
        targetLevel = approach(targetLevel, candidate, attack)

        if (System.currentTimeMillis() - lastPushAt > 20L) {
            levelHistory[writeIndex] = targetLevel
            writeIndex = (writeIndex + 1) % historySize
            lastPushAt = System.currentTimeMillis()
        }
    }

    fun setReactiveMode() {
        renderMode = RenderMode.Reactive
        targetLevel = 0.04f
        currentLevel = 0.04f
    }

    fun setAmbientMode() {
        renderMode = RenderMode.Ambient
        ambientBase = 0.15f
        targetLevel = ambientBase
    }

    fun setIdleMode() {
        renderMode = RenderMode.Idle
        targetLevel = 0.02f
        currentLevel = 0.02f
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

    private enum class RenderMode {
        Idle,
        Reactive,
        Ambient
    }
}
