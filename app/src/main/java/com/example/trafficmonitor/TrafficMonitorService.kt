package com.example.trafficmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground-сервис: раз в минуту смотрит расход за месяц,
 * обновляет постоянное уведомление и показывает предупреждения о лимите.
 */
class TrafficMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val isChecking = AtomicBoolean(false)
    private lateinit var prefs: Prefs

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.monitoringEnabled = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!prefs.monitoringEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Это нужно вызвать сразу, иначе Android убьёт сервис.
        startForeground(
            STATUS_NOTIFICATION_ID,
            buildStatusNotification(getString(R.string.notification_loading))
        )

        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshStatus() {
        if (!prefs.monitoringEnabled) {
            stopSelf()
            return
        }

        if (!UsageAccess.has(this)) {
            updateStatusNotification(getString(R.string.notification_no_access))
            return
        }

        // Запрос к NetworkStatsManager может занять несколько секунд,
        // поэтому не делаем его в главном потоке.
        if (!isChecking.compareAndSet(false, true)) return

        worker.execute {
            val result = runCatching { TrafficRepository.monthTotal(this) }
            isChecking.set(false)

            handler.post {
                if (!prefs.monitoringEnabled) return@post

                result.onSuccess { monthBytes ->
                    val currentLimitMb = prefs.limitMb
                    val text = if (currentLimitMb > 0) {
                        getString(
                            R.string.notification_month_usage,
                            Format.size(monthBytes),
                            currentLimitMb
                        )
                    } else {
                        getString(
                            R.string.notification_month_usage_no_limit,
                            Format.size(monthBytes)
                        )
                    }

                    updateStatusNotification(text)
                    checkLimit(monthBytes, currentLimitMb)
                }.onFailure { error ->
                    Log.e(TAG, "Не удалось получить статистику", error)
                    updateStatusNotification(getString(R.string.notification_error))
                }
            }
        }
    }

    private fun checkLimit(monthBytes: Long, limitMb: Long) {
        if (limitMb <= 0) return

        val limitBytes = limitMb * 1024L * 1024L

        if (monthBytes >= limitBytes) {
            val text = getString(
                R.string.warning_100_text,
                Format.size(monthBytes),
                limitMb
            )
            showWarningOnce(100, getString(R.string.warning_100_title), text)
        } else {
            val percent = ((monthBytes * 100.0) / limitBytes).toInt()
            if (percent >= 80) {
                val left = limitBytes - monthBytes
                val text = getString(
                    R.string.warning_80_text,
                    percent,
                    Format.size(left)
                )
                showWarningOnce(80, getString(R.string.warning_80_title), text)
            }
        }
    }

    private fun showWarningOnce(level: Int, title: String, text: String) {
        if (prefs.wasWarned(level)) return

        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        try {
            manager.notify(WARNING_NOTIFICATION_ID, buildWarningNotification(title, text))
            prefs.markWarned(level)
        } catch (e: SecurityException) {
            Log.w(TAG, "Нет разрешения на уведомления", e)
        }
    }

    private fun updateStatusNotification(text: String) {
        try {
            NotificationManagerCompat.from(this)
                .notify(STATUS_NOTIFICATION_ID, buildStatusNotification(text))
        } catch (e: SecurityException) {
            Log.w(TAG, "Не удалось обновить уведомление", e)
        }
    }

    private fun buildStatusNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle(getString(R.string.notification_status_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_traffic)
            .setColor(ContextCompat.getColor(this, R.color.primary))
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_stop), stopPendingIntent())
            .build()
    }

    private fun buildWarningNotification(title: String, text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_WARNINGS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_traffic)
            .setColor(ContextCompat.getColor(this, R.color.danger))
            .setContentIntent(contentPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, TrafficMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)

        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_status_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(statusChannel)

        val warningChannel = NotificationChannel(
            CHANNEL_WARNINGS,
            getString(R.string.channel_warnings_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.channel_warnings_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(warningChannel)
    }

    companion object {
        private const val TAG = "TrafficMonitorService"

        private const val CHANNEL_STATUS = "traffic_status"
        private const val CHANNEL_WARNINGS = "traffic_warnings"

        private const val STATUS_NOTIFICATION_ID = 1
        private const val WARNING_NOTIFICATION_ID = 2

        private const val CHECK_INTERVAL_MS = 60_000L
        private const val ACTION_STOP = "com.example.trafficmonitor.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TrafficMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrafficMonitorService::class.java))
        }
    }
}
