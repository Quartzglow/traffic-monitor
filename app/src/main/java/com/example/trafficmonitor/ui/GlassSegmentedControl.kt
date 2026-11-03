package com.example.trafficmonitor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.trafficmonitor.R

/**
 * Стеклянный сегмент-переключатель с «пузырьком», который плавно
 * переезжает к выбранному пункту.
 */
class GlassSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val items = mutableListOf<String>()
    private var selectedIndex = 0
    private var bubblePos = 0f
    private var bubbleAnimator: ValueAnimator? = null

    private var onSelected: ((Int) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
        color = context.colorOf(R.color.glass_stroke_top)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
        color = context.colorOf(R.color.chip_selected_border)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.sp(13f)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.sp(13f)
        typeface = context.typefaceOf(R.font.manrope_bold)
        color = context.colorOf(R.color.text_primary)
    }
    private val rect = RectF()
    private val pad = context.dp(4f)

    private var trackShader: Shader? = null
    private var bubbleShader: Shader? = null

    init {
        isClickable = true
        textPaint.typeface = context.typefaceOf(R.font.manrope_medium)
        textPaint.color = context.colorOf(R.color.text_secondary)
    }

    fun setItems(labels: List<String>, selected: Int = 0) {
        items.clear()
        items += labels
        selectedIndex = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        bubblePos = selectedIndex.toFloat()
        requestLayout()
        buildShaders()
        invalidate()
    }

    fun setOnSelected(listener: (Int) -> Unit) {
        onSelected = listener
    }

    fun select(index: Int, notify: Boolean = true) {
        if (index !in items.indices || index == selectedIndex) return
        selectedIndex = index
        bubbleAnimator?.cancel()
        bubbleAnimator = ValueAnimator.ofFloat(bubblePos, index.toFloat()).apply {
            duration = 280L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                bubblePos = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (notify) onSelected?.invoke(index)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            context.dp(46f).toInt() + paddingTop + paddingBottom
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShaders()
    }

    /** Шейдеры пересоздаются только при смене размера — в onDraw аллокаций нет. */
    private fun buildShaders() {
        if (height <= 0 || width <= 0) return
        trackShader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            context.colorOf(R.color.glass_top),
            context.colorOf(R.color.glass_bottom),
            Shader.TileMode.CLAMP
        )
        if (items.isNotEmpty()) {
            val segWidth = ((width - pad * 2) / items.size).coerceAtLeast(1f)
            bubbleShader = LinearGradient(
                0f, 0f, segWidth, 0f,
                context.colorOf(R.color.chip_selected_start),
                context.colorOf(R.color.chip_selected_end),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val r = (height - pad * 2) / 2f
        rect.set(pad, pad, width - pad, height - pad)

        trackPaint.shader = trackShader
        canvas.drawRoundRect(rect, r, r, trackPaint)
        canvas.drawRoundRect(rect, r, r, trackStroke)

        val segWidth = (width - pad * 2) / items.size
        val bubbleLeft = pad + segWidth * bubblePos
        rect.set(0f, pad, segWidth.toFloat(), height - pad)

        // Пузырёк рисуем в собственной системе координат, чтобы градиент
        // всегда заполнял его целиком.
        bubblePaint.shader = bubbleShader
        canvas.save()
        canvas.translate(bubbleLeft, 0f)
        canvas.drawRoundRect(rect, r, r, bubblePaint)
        canvas.drawRoundRect(rect, r, r, bubbleStroke)
        canvas.restore()

        val centerY = height / 2f - (selectedPaint.ascent() + selectedPaint.descent()) / 2f
        items.forEachIndexed { index, label ->
            val centerX = pad + segWidth * index + segWidth / 2f
            val paint = if (index == selectedIndex) selectedPaint else textPaint
            canvas.drawText(label, centerX, centerY, paint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (items.isEmpty()) return false
                val segWidth = (width - pad * 2) / items.size
                val index = ((event.x - pad) / segWidth).toInt().coerceIn(0, items.lastIndex)
                if (index != selectedIndex) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    select(index)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
