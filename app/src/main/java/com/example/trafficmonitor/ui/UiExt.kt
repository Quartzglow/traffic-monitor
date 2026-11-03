package com.example.trafficmonitor.ui

import android.content.Context
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/** Плотность в dp. */
fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

/** Плотность в px (округлённо). */
fun Context.dpInt(value: Float): Int = (value * resources.displayMetrics.density).toInt()

/** Размер шрифта в px. */
fun Context.sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

fun Context.colorOf(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

fun Context.typefaceOf(@FontRes id: Int): Typeface? = ResourcesCompat.getFont(this, id)

/**
 * Нажатие: лёгкое сжатие + тактильный отклик. Не мешает клику.
 */
fun View.onPressFeedback(action: () -> Unit) {
    setOnClickListener {
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        action()
    }
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }
        false
    }
}

/** Появление элемента: fade + подъём. */
fun View.fadeSlideIn(delay: Long = 0L, distancePx: Float = context.dp(14f)) {
    alpha = 0f
    translationY = distancePx
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delay)
        .setDuration(420L)
        .setInterpolator(FastOutSlowInInterpolator())
        .start()
}
