package com.example.trafficmonitor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Бегущий блик поверх скелетона загрузки.
 */
class ShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var shader: LinearGradient? = null

    private val highlight = context.colorOf(com.example.trafficmonitor.R.color.glass_highlight)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                (highlight and 0x00FFFFFF),
                (highlight and 0x00FFFFFF) or 0x26FFFFFF,
                (highlight and 0x00FFFFFF)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1300L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val current = shader ?: return

        // Блик «проезжает» сдвигом canvas — без создания шейдера в каждом кадре.
        paint.shader = current
        canvas.save()
        canvas.translate(-width + width * 2f * phase, 0f)
        canvas.drawRect(rect, paint)
        canvas.restore()
    }
}
