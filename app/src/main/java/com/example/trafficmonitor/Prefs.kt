package com.example.trafficmonitor

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Небольшое хранилище настроек на SharedPreferences.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("traffic_monitor", Context.MODE_PRIVATE)

    var limitMb: Long
        get() = prefs.getLong(KEY_LIMIT_MB, 0L)
        set(value) = prefs.edit().putLong(KEY_LIMIT_MB, value).apply()

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING, value).apply()

    /** 0 — как в системе, 1 — светлая, 2 — тёмная. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_DARK)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    fun wasWarned(level: Int): Boolean = prefs.getBoolean(warningKey(level), false)

    fun markWarned(level: Int) {
        prefs.edit().putBoolean(warningKey(level), true).apply()
    }

    /** После изменения лимита предупреждения можно показывать заново. */
    fun clearWarnings() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("warned_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun warningKey(level: Int): String {
        // Ключ содержит месяц, поэтому в новом месяце предупреждения снова сработают.
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        return "warned_${level}_$month"
    }

    companion object {
        private const val KEY_LIMIT_MB = "limit_mb"
        private const val KEY_MONITORING = "monitoring_enabled"
        private const val KEY_THEME_MODE = "theme_mode"

        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
    }
}
