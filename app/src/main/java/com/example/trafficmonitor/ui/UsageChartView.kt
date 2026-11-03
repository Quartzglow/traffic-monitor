package com.example.trafficmonitor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.trafficmonitor.R
import kotlin.math.ceil

/**
 * Столбчатый график расхода с градиентной заливкой и анимацией появления.
 * Без сторонних библиотек — рисуется в Canvas.
 */
class UsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Long> = emptyList()
    private var labels: List<String> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_tertiary)
        textSize = context.sp(10f)
        typeface = context.typefaceOf(R.font.manrope_medium)
        textAlign = Paint.Align.CENTER
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.text_tertiary)
        textSize = context.sp(12f)
        typeface = context.typefaceOf(R.font.manrope_medium)
        textAlign = Paint.Align.CENTER
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.colorOf(R.color.glass_stroke_bottom)
        strokeWidth = context.dp(1f)
    }
    private val rect = RectF()

    var startColor: Int = context.colorOf(R.color.accent_cyan)
        set(value) {
            field = value
            buildShader()
        }

    var endColor: Int = context.colorOf(R.color.accent_violet)
        set(value) {
            field = value
            buildShader()
        }

    private var animFactor = 0f
    private var animator: ValueAnimator? = null
    private var barShader: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShader()
    }

    /** Градиент столбцов создаём один раз на размер/цвет, а не в каждом кадре. */
    private fun buildShader() {
        if (height <= 0) return
        val top = context.dp(8f)
        val bottom = height - context.dp(22f)
        barShader = LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(
                startColor,
                endColor,
                Color.argb(40, Color.red(endColor), Color.green(endColor), Color.blue(endColor))
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        barPaint.shader = barShader
        invalidate()
    }

    fun setData(values: List<Long>, labels: List<String>, animate: Boolean = true) {
        this.values = values
        this.labels = labels
        contentDescription = if (values.any { it > 0 }) {
            "График расхода трафика"
        } else {
            "Нет данных за выбранный период"
        }
        if (animate) animateIn() else {
            animFactor = 1f
            invalidate()
        }
    }

    fun animateIn() {
        animator?.cancel()
        animFactor = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 780L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                animFactor = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val chartBottom = height - context.dp(22f)
        val chartTop = context.dp(8f)
        val areaHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        canvas.drawLine(0f, chartBottom, width.toFloat(), chartBottom, baselinePaint)

        if (values.isEmpty() || values.maxOrNull() == 0L) {
            val baseline = chartBottom - areaHeight / 2f -
                (emptyPaint.ascent() + emptyPaint.descent()) / 2f
            canvas.drawText("Нет данных за период", width / 2f, baseline, emptyPaint)
            return
        }

        val maxValue = values.maxOrNull()!!.coerceAtLeast(1L).toFloat()
        val slot = width.toFloat() / values.size
        val barWidth = (slot * 0.56f).coerceAtMost(context.dp(18f)).coerceAtLeast(context.dp(4f))

        if (barShader == null) buildShader()

        val labelStep = ceil(values.size / 6f).toInt().coerceAtLeast(1)

        values.forEachIndexed { index, value ->
            val centerX = slot * index + slot / 2f
            val norm = (value / maxValue).coerceIn(0f, 1f)
            val barHeight = (norm * areaHeight * animFactor).coerceAtLeast(if (value > 0) context.dp(3f) else 0f)

            if (barHeight > 0f) {
                rect.set(
                    centerX - barWidth / 2f,
                    chartBottom - barHeight,
                    centerX + barWidth / 2f,
                    chartBottom
                )
                canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint)
            }

            if (labels.isNotEmpty() && (index % labelStep == 0 || index == values.lastIndex)) {
                val label = labels.getOrNull(index) ?: ""
                if (label.isNotEmpty()) {
                    canvas.drawText(label, centerX, chartBottom + context.dp(16f), labelPaint)
                }
            }
        }
    }
}
