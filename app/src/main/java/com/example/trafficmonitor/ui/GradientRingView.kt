package com.example.trafficmonitor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.trafficmonitor.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Дуговой индикатор расхода: 270°, градиентная обводка, свечение
 * и светящаяся точка на конце дуги. Прогресс анимируется плавно.
 */
class GradientRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        private set

    var startColor: Int = context.colorOf(R.color.accent_teal)
        set(value) {
            field = value
            buildShader()
        }

    var endColor: Int = context.colorOf(R.color.accent_violet)
        set(value) {
            field = value
            buildShader()
        }

    var pulse: Boolean = false
        set(value) {
            field = value
            if (!value) pulseAnimator?.cancel()
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.colorOf(R.color.ring_track)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val matrix = Matrix()
    private var shader: SweepGradient? = null

    private var animator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val strokeWidth = context.dp(15f)
    private val startAngle = 135f
    private val sweepAngle = 270f

    init {
        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth
        glowPaint.strokeWidth = strokeWidth * 2.6f
    }

    /** Анимирует прогресс. target — доля 0..1. */
    fun animateToProgress(target: Float, duration: Long = 900L) {
        val clamped = target.coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, clamped).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setProgressImmediate(value: Float) {
        animator?.cancel()
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    private fun buildShader() {
        if (rect.width() <= 0f) return
        shader = SweepGradient(
            rect.centerX(),
            rect.centerY(),
            intArrayOf(startColor, endColor),
            floatArrayOf(0f, sweepAngle / 360f)
        ).also {
            matrix.reset()
            matrix.postRotate(startAngle, rect.centerX(), rect.centerY())
            it.setLocalMatrix(matrix)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val glowSpace = strokeWidth * 1.7f
        rect.set(glowSpace, glowSpace, w - glowSpace, h - glowSpace)
        buildShader()
    }

    private fun ensurePulse() {
        if (pulseAnimator != null) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        if (rect.width() <= 0f) return

        canvas.drawArc(rect, startAngle, sweepAngle, false, trackPaint)

        if (progress <= 0.001f) return

        if (pulse) ensurePulse()
        val phase = if (pulse) {
            (sin((SystemClock.elapsedRealtime() % 1800L) / 1800.0 * 2.0 * Math.PI) + 1.0).toFloat() / 2f
        } else {
            1f
        }

        val sweep = sweepAngle * progress
        glowPaint.color = Color.argb(
            (40 + 50 * phase).toInt(),
            Color.red(endColor),
            Color.green(endColor),
            Color.blue(endColor)
        )
        canvas.drawArc(rect, startAngle, sweep, false, glowPaint)

        arcPaint.shader = shader
        canvas.drawArc(rect, startAngle, sweep, false, arcPaint)

        val angle = Math.toRadians((startAngle + sweep).toDouble())
        val radius = rect.width() / 2f
        val dotX = rect.centerX() + cos(angle).toFloat() * radius
        val dotY = rect.centerY() + sin(angle).toFloat() * radius

        dotPaint.color = Color.argb(
            (60 + 60 * phase).toInt(),
            Color.red(endColor),
            Color.green(endColor),
            Color.blue(endColor)
        )
        canvas.drawCircle(dotX, dotY, strokeWidth * 1.0f, dotPaint)

        dotPaint.color = Color.WHITE
        canvas.drawCircle(dotX, dotY, strokeWidth * 0.34f, dotPaint)
    }
}
