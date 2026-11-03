package com.example.trafficmonitor

import java.util.Locale

/**
 * Форматирование байтов в мегабайты/гигабайты.
 */
object Format {

    fun size(bytes: Long): String {
        val (value, unit) = split(bytes)
        return "$value $unit"
    }

    /** Число и единица отдельно — для крупных цифр и count-up анимации. */
    fun split(bytes: Long): Pair<String, String> {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024.0 -> String.format(Locale.getDefault(), "%.2f", mb / 1024.0) to "ГБ"
            mb >= 100.0 -> String.format(Locale.getDefault(), "%.0f", mb) to "МБ"
            else -> String.format(Locale.getDefault(), "%.1f", mb) to "МБ"
        }
    }

    /** Только число, без единицы. */
    fun numeric(bytes: Long): String = split(bytes).first
}
