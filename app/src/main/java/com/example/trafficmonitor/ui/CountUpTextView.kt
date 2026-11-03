package com.example.trafficmonitor.ui

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * TextView с анимированным «набеганием» числа.
 */
class CountUpTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var animator: ValueAnimator? = null
    private var currentValue = 0L
    private var formatter: (Long) -> String = { it.toString() }

    fun setFormatter(format: (Long) -> String) {
        formatter = format
    }

    fun animateToValue(target: Long, duration: Long = 850L) {
        animator?.cancel()
        val from = currentValue
        if (from == target) {
            text = formatter(target)
            return
        }
        animator = ValueAnimator.ofFloat(from.toFloat(), target.toFloat()).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                currentValue = (it.animatedValue as Float).toLong().coerceAtLeast(0L)
                text = formatter(currentValue)
            }
            start()
        }
    }

    fun setValueImmediate(value: Long) {
        animator?.cancel()
        currentValue = value
        text = formatter(value)
    }
}
