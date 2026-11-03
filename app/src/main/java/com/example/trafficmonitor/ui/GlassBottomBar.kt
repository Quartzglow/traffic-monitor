package com.example.trafficmonitor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.trafficmonitor.R

/**
 * Плавающая стеклянная «капсула» навигации с переезжающей подсветкой.
 */
class GlassBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class NavItem(val iconRes: Int, val label: String, val contentDescription: String)

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dpInt(8f), 0, context.dpInt(8f), 0)
    }
    private val pill = View(context)
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()

    private var items: List<NavItem> = emptyList()
    private var selectedIndex = 0
    private var onSelected: ((Int) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f)
    }
    private val rect = RectF()

    init {
        setWillNotDraw(false)
        elevation = context.dp(16f)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }

        pill.background = ContextCompat.getDrawable(context, R.drawable.bg_nav_pill)
        addView(pill, LayoutParams(0, 0))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setItems(newItems: List<NavItem>, selected: Int = 0) {
        items = newItems
        row.removeAllViews()
        icons.clear()
        labels.clear()

        items.forEachIndexed { index, item ->
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                contentDescription = item.contentDescription
            }
            val icon = ImageView(context).apply {
                setImageResource(item.iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val label = TextView(context).apply {
                text = item.label
                textSize = 10.5f
                gravity = Gravity.CENTER
                includeFontPadding = false
                typeface = context.typefaceOf(R.font.manrope_semibold)
            }
            column.addView(icon, LinearLayout.LayoutParams(context.dpInt(23f), context.dpInt(23f)))
            column.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = context.dpInt(3f) }
            )

            column.onPressFeedback {
                if (index != selectedIndex) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    select(index)
                }
            }

            row.addView(
                column,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            icons += icon
            labels += label
        }

        selectedIndex = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        post { updateSelection(false) }
    }

    fun setOnSelected(listener: (Int) -> Unit) {
        onSelected = listener
    }

    fun select(index: Int, notify: Boolean = true) {
        if (index !in items.indices || index == selectedIndex) return
        selectedIndex = index
        updateSelection(true)
        if (notify) onSelected?.invoke(index)
    }

    private fun updateSelection(animate: Boolean) {
        if (items.isEmpty()) return
        val rowWidth = width - row.paddingLeft - row.paddingRight
        if (rowWidth <= 0) return

        val itemWidth = rowWidth.toFloat() / items.size
        val pillWidth = itemWidth - context.dp(12f)
        val pillHeight = height - context.dp(20f)
        val left = row.paddingLeft + itemWidth * selectedIndex + (itemWidth - pillWidth) / 2f
        val top = (height - pillHeight) / 2f

        pill.layout(0, top.toInt(), pillWidth.toInt(), (top + pillHeight).toInt())

        if (animate) {
            pill.animate()
                .translationX(left)
                .setDuration(300L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            pill.translationX = left
        }

        val active = context.colorOf(R.color.accent_violet)
        val idle = context.colorOf(R.color.text_secondary)

        icons.forEachIndexed { index, icon ->
            val isActive = index == selectedIndex
            icon.setColorFilter(if (isActive) active else idle)
            icon.animate()
                .scaleX(if (isActive) 1.12f else 1f)
                .scaleY(if (isActive) 1.12f else 1f)
                .setDuration(220L)
                .start()
            labels[index].setTextColor(if (isActive) context.colorOf(R.color.text_primary) else idle)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            context.colorOf(R.color.nav_glass_top),
            context.colorOf(R.color.nav_glass_bottom),
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            context.colorOf(R.color.nav_stroke_top),
            context.colorOf(R.color.nav_stroke_bottom),
            Shader.TileMode.CLAMP
        )
        post { updateSelection(false) }
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0.5f, 0.5f, width - 0.5f, height - 0.5f)
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, strokePaint)
        super.onDraw(canvas)
    }
}
