package com.example.trafficmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.example.trafficmonitor.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Живой mesh-градиент: несколько крупных цветных пятен медленно
 * дрейфуют по экрану. Рисуется напрямую в Canvas — без RenderEffect,
 * поэтому одинаково работает на Android 6+ и не бьёт по FPS.
 */
class MeshBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Blob(
        val color: Int,
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val driftX: Float,
        val driftY: Float,
        val speed: Float,
        val phase: Float
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobs = ArrayList<Blob>(6)
    private val shaders = ArrayList<Shader>(6)
    private val startedAt = SystemClock.elapsedRealtime()

    init {
        val alpha = if (isDark()) 150 else 120
        // x/y — доля от размера экрана, radius — доля от ширины.
        blobs += Blob(tint(R.color.mesh_purple, alpha), 0.12f, 0.08f, 0.78f, 0.08f, 0.05f, 0.11f, 0.0f)
        blobs += Blob(tint(R.color.mesh_cyan, alpha), 0.92f, 0.18f, 0.62f, 0.06f, 0.07f, 0.09f, 1.6f)
        blobs += Blob(tint(R.color.mesh_coral, alpha), 0.85f, 0.78f, 0.60f, 0.09f, 0.05f, 0.13f, 3.1f)
        blobs += Blob(tint(R.color.mesh_lime, (alpha * 0.6f).toInt()), 0.08f, 0.85f, 0.55f, 0.07f, 0.08f, 0.07f, 4.4f)
        blobs += Blob(tint(R.color.mesh_blue, (alpha * 0.8f).toInt()), 0.55f, 0.45f, 0.85f, 0.05f, 0.05f, 0.10f, 5.7f)
    }

    private fun isDark(): Boolean {
        val nightMask = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun tint(colorRes: Int, alpha: Int): Int {
        val base = context.colorOf(colorRes)
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(base),
            Color.green(base),
            Color.blue(base)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            context.colorOf(R.color.bg_top),
            context.colorOf(R.color.bg_bottom),
            Shader.TileMode.CLAMP
        )

        // Шейдеры создаём один раз: в onDraw только сдвигаем canvas.
        shaders.clear()
        blobs.forEach { blob ->
            val radius = w * blob.radius
            shaders += RadialGradient(
                0f, 0f, radius,
                intArrayOf(blob.color, (blob.color and 0x00FFFFFF) or 0x2E000000, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (!isShown || shaders.isEmpty()) return

        val t = (SystemClock.elapsedRealtime() - startedAt) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()

        for (i in blobs.indices) {
            val blob = blobs[i]
            val cx = w * blob.cx + sin(t * blob.speed + blob.phase) * w * blob.driftX
            val cy = h * blob.cy + cos(t * blob.speed * 0.8f + blob.phase) * h * blob.driftY

            paint.shader = shaders[i]
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, w * blob.radius, paint)
            canvas.restore()
        }

        postInvalidateDelayed(33L)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) postInvalidateDelayed(33L)
    }
}
