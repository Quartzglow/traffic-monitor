package com.example.trafficmonitor

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Проверка специального разрешения "Доступ к статистике использования".
 * Обычный runtime-permission здесь не работает: пользователь выдаёт доступ в настройках.
 */
object UsageAccess {

    fun has(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }
}
