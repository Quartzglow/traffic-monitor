package com.example.trafficmonitor

import android.graphics.drawable.Drawable

/**
 * Одна строка в списке приложений.
 */
data class AppUsage(
    val uid: Int,
    val packageName: String,
    val label: String,
    val bytes: Long,
    val icon: Drawable?
)
