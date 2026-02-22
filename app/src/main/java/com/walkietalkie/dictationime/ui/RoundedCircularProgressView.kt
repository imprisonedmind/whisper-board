package com.walkietalkie.dictationime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.walkietalkie.dictationime.R
import kotlin.math.min

class RoundedCircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var strokeWidthPx = dpToPx(10f)

    @ColorInt
    private var trackColor: Int = context.getColor(R.color.accent_soft)

    @ColorInt
    private var indicatorColor: Int = context.getColor(R.color.accent)

    private var progress = 0.65f

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.RoundedCircularProgressView,
                defStyleAttr,
                0
            )
            progress = typedArray.getFloat(
                R.styleable.RoundedCircularProgressView_rcp_progress,
                progress
            )
            trackColor = typedArray.getColor(
                R.styleable.RoundedCircularProgressView_rcp_trackColor,
                trackColor
            )
            indicatorColor = typedArray.getColor(
                R.styleable.RoundedCircularProgressView_rcp_indicatorColor,
                indicatorColor
            )
            strokeWidthPx = typedArray.getDimension(
                R.styleable.RoundedCircularProgressView_rcp_strokeWidth,
                strokeWidthPx
            )
            typedArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height)
        val radius = size / 2f - strokeWidthPx / 2f
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        paint.strokeWidth = strokeWidthPx

        paint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, paint)

        paint.color = indicatorColor
        val clamped = progress.coerceIn(0f, 1f)
        canvas.drawArc(rect, -90f, 360f * clamped, false, paint)
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
