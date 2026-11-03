package com.example.trafficmonitor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Применяем сохранённую тему до создания Activity.
 */
class TrafficApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val mode = Prefs(this).themeMode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
