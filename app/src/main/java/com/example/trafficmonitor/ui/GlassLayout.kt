package com.example.trafficmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout
import com.example.trafficmonitor.R

/**
 * «Стеклянная» карточка: полупрозрачный градиент, светлая обводка
 * и тонкий блик сверху. Содержимое обрезается по скруглённому контуру.
 */
class GlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    var cornerRadius: Float = context.dp(28f)
        set(value) {
            field = value
            invalidateOutline()
            invalidate()
        }

    var glassShadow: Boolean = true
        set(value) {
            field = value
            elevation = if (value) context.dp(10f) else 0f
        }

    private val colors = intArrayOf(
        context.colorOf(R.color.glass_stroke_top),
        context.colorOf(R.color.glass_stroke_bottom)
    )

    init {
        setWillNotDraw(false)
        val typed = context.obtainStyledAttributes(attrs, R.styleable.GlassLayout)
        cornerRadius = typed.getDimension(R.styleable.GlassLayout_glassRadius, cornerRadius)
        glassShadow = typed.getBoolean(R.styleable.GlassLayout_glassShadow, true)
        typed.recycle()

        elevation = if (glassShadow) context.dp(10f) else 0f
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            context.colorOf(R.color.glass_top),
            context.colorOf(R.color.glass_bottom),
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            colors[0], colors[1], Shader.TileMode.CLAMP
        )
        highlightPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(
                (context.colorOf(R.color.glass_highlight) and 0x00FFFFFF),
                context.colorOf(R.color.glass_highlight),
                (context.colorOf(R.color.glass_highlight) and 0x00FFFFFF)
            ),
            floatArrayOf(0.1f, 0.5f, 0.9f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = cornerRadius
        val inset = strokePaint.strokeWidth / 2f

        canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, fillPaint)
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, strokePaint)

        if (h > 0) {
            clipPath.reset()
            clipPath.addRoundRect(inset, inset, w - inset, h - inset, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(inset, inset, w - inset, inset + context.dp(1.2f), highlightPaint)
            canvas.restore()
        }

        super.onDraw(canvas)
    }
}
