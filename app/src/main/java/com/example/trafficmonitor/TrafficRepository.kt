package com.example.trafficmonitor

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import java.util.Calendar

/**
 * Получение статистики мобильного трафика через NetworkStatsManager.
 *
 * Для работы нужно разрешение PACKAGE_USAGE_STATS (выдаётся в настройках телефона).
 * subscriberId = null означает "все SIM-карты / SIM по умолчанию".
 */
object TrafficRepository {

    data class Summary(
        val monthBytes: Long,
        val todayBytes: Long,
        val apps: List<AppUsage>
    )

    fun load(context: Context): Summary {
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val now = System.currentTimeMillis()
        val monthStart = startOfMonth()

        val monthBytes = queryDeviceTotal(manager, monthStart, now)
        val todayBytes = queryDeviceTotal(manager, startOfToday(), now)
        val apps = loadApps(context, manager, monthStart, now)

        return Summary(monthBytes, todayBytes, apps)
    }

    /** Нужно сервису: только общий расход за текущий месяц. */
    fun monthTotal(context: Context): Long {
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        return queryDeviceTotal(manager, startOfMonth(), System.currentTimeMillis())
    }

    private fun queryDeviceTotal(
        manager: NetworkStatsManager,
        start: Long,
        end: Long
    ): Long {
        // Для устройства система сразу возвращает готовый Bucket с общим расходом.
        val bucket = manager.querySummaryForDevice(
            ConnectivityManager.TYPE_MOBILE,
            null,
            start,
            end
        ) ?: return 0L
        return bucket.rxBytes + bucket.txBytes
    }

    private fun loadApps(
        context: Context,
        manager: NetworkStatsManager,
        start: Long,
        end: Long
    ): List<AppUsage> {
        val perUid = HashMap<Int, Long>()
        val stats = manager.querySummary(
            ConnectivityManager.TYPE_MOBILE,
            null,
            start,
            end
        ) ?: return emptyList()
        val bucket = NetworkStats.Bucket()

        try {
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)

                // Отрицательные uid — это служебные записи (tethered, removed и т.п.).
                if (bucket.uid < 0) continue

                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0) {
                    perUid[bucket.uid] = (perUid[bucket.uid] ?: 0L) + bytes
                }
            }
        } finally {
            stats.close()
        }

        val packageManager = context.packageManager
        return perUid.entries
            .sortedByDescending { it.value }
            .take(30)
            .map { (uid, bytes) ->
                val packageName = packageManager.getNameForUid(uid)
                val appInfo = packageName?.let {
                    try {
                        packageManager.getApplicationInfo(it, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }

                val label = appInfo?.let {
                    packageManager.getApplicationLabel(it).toString()
                } ?: packageName ?: "Системный процесс"

                val icon = appInfo?.let {
                    try {
                        packageManager.getApplicationIcon(it)
                    } catch (e: Exception) {
                        null
                    }
                }

                AppUsage(
                    uid = uid,
                    packageName = packageName ?: "UID $uid",
                    label = label,
                    bytes = bytes,
                    icon = icon
                )
            }
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfMonth(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
