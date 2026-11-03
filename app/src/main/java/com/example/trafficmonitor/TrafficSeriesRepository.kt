package com.example.trafficmonitor

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Ряды данных для графика. Бизнес-логика существующих экранов не меняется:
 * это отдельный, только читающий слой поверх NetworkStatsManager.
 */
object TrafficSeriesRepository {

    enum class Period { DAY, WEEK, MONTH }

    data class Series(
        val values: List<Long>,
        val labels: List<String>,
        val total: Long
    )

    fun load(context: Context, period: Period): Series {
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptySeries(period)

        return try {
            when (period) {
                Period.DAY -> loadDay(manager)
                Period.WEEK -> loadWeek(manager)
                Period.MONTH -> loadMonth(manager)
            }
        } catch (e: Exception) {
            emptySeries(period)
        }
    }

    /** Пустые подписи, чтобы график не «прыгал» при отсутствии доступа. */
    fun emptySeries(period: Period): Series {
        val labels = when (period) {
            Period.DAY -> (0..23).map { String.format(Locale.US, "%02d", it) }
            Period.WEEK -> weekdayLabels(7)
            Period.MONTH -> monthDayLabels(Calendar.getInstance())
        }
        return Series(List(labels.size) { 0L }, labels, 0L)
    }

    private fun loadDay(manager: NetworkStatsManager): Series {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val startOfDay = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val values = ArrayList<Long>(currentHour + 1)
        val labels = ArrayList<String>(currentHour + 1)

        for (hour in 0..currentHour) {
            val bucketStart = (startOfDay.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, hour)
            }
            val bucketEnd = (bucketStart.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, 1)
            }
            values += query(manager, bucketStart.timeInMillis, bucketEnd.timeInMillis)
            labels += String.format(Locale.US, "%02d", hour)
        }
        return Series(values, labels, values.sum())
    }

    private fun loadWeek(manager: NetworkStatsManager): Series {
        val values = ArrayList<Long>(7)
        val labels = ArrayList<String>(7)

        val today = Calendar.getInstance()
        for (offset in 6 downTo 0) {
            val day = (today.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val start = startOfDay(day)
            val end = start + 24 * 60 * 60 * 1000L
            values += query(manager, start, end)
            labels += dayLabel(day)
        }
        return Series(values, labels, values.sum())
    }

    private fun loadMonth(manager: NetworkStatsManager): Series {
        val today = Calendar.getInstance()
        val currentDay = today.get(Calendar.DAY_OF_MONTH)

        val values = ArrayList<Long>(currentDay)
        val labels = ArrayList<String>(currentDay)

        for (day in 1..currentDay) {
            val calendar = (today.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, day)
            }
            val start = startOfDay(calendar)
            val end = start + 24 * 60 * 60 * 1000L
            values += query(manager, start, end)
            labels += day.toString()
        }

        // В начале месяца добавляем пустые дни, чтобы график не выглядел обрезанным.
        if (currentDay < 7) {
            for (day in currentDay + 1..7) {
                values += 0L
                labels += day.toString()
            }
        }
        return Series(values, labels, values.sum())
    }

    private fun query(manager: NetworkStatsManager, start: Long, end: Long): Long {
        val bucket = manager.querySummaryForDevice(
            ConnectivityManager.TYPE_MOBILE,
            null,
            start,
            end
        ) ?: return 0L
        return (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
    }

    private fun startOfDay(calendar: Calendar): Long {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun dayLabel(calendar: Calendar): String {
        val format = SimpleDateFormat("EE", Locale("ru"))
        return format.format(Date(calendar.timeInMillis))
            .replace(".", "")
            .take(2)
            .replaceFirstChar { it.uppercase(Locale("ru")) }
    }

    private fun weekdayLabels(count: Int): List<String> {
        val calendar = Calendar.getInstance()
        return (count - 1 downTo 0).map { offset ->
            val day = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            dayLabel(day)
        }
    }

    private fun monthDayLabels(calendar: Calendar): List<String> {
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return (1..day).map { it.toString() }
    }
}
