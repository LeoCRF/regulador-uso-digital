package com.example.regulador_uso_digital.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import java.util.*

class UsageStatsHelper(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    
    companion object {
        private val nameCache = mutableMapOf<String, String>()
        private val iconCache = mutableMapOf<String, Drawable>()
        private val launchableCache = mutableMapOf<String, Boolean>()
    }

    data class AppBasicInfo(val name: String, val icon: Drawable)

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsagePermission(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAppBasicInfo(packageName: String): AppBasicInfo? {
        val name = nameCache[packageName]
        val icon = iconCache[packageName]
        if (name != null && icon != null) return AppBasicInfo(name, icon)

        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(ai).toString()
            val appIcon = pm.getApplicationIcon(ai)
            nameCache[packageName] = appName
            iconCache[packageName] = appIcon
            AppBasicInfo(appName, appIcon)
        } catch (e: Exception) {
            val fallbackName = packageName.split(".").lastOrNull() ?: packageName
            AppBasicInfo(fallbackName, context.packageManager.defaultActivityIcon)
        }
    }

    /**
     * Uso de hoje com precisão por eventos.
     */
    fun getUsageStatsToday(filterRealApps: Boolean = true): Map<String, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return getTimeByEvents(cal.timeInMillis, now, filterRealApps)
    }

    /**
     * Busca o uso da semana usando Agregação do Sistema.
     * Método mais robusto para garantir que NADA fique de fora na aba Apps.
     */
    fun getUsageStatsWeekly(filterRealApps: Boolean = false): Map<String, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        
        val stats = usageStatsManager.queryAndAggregateUsageStats(cal.timeInMillis, now)
        val resultMap = mutableMapOf<String, Long>()
        
        for ((pkg, usageStats) in stats) {
            val time = usageStats.totalTimeInForeground
            if (time > 0) {
                // Se filterRealApps for false, aceitamos TUDO.
                if (!filterRealApps || isRealUserApp(pkg)) {
                    resultMap[pkg] = (resultMap[pkg] ?: 0L) + time
                }
            }
        }
        return resultMap
    }

    /**
     * Motor de cálculo por eventos (Alta precisão para hoje).
     */
    fun getTimeByEvents(startTime: Long, endTime: Long, filterRealApps: Boolean = true): Map<String, Long> {
        val stats = mutableMapOf<String, Long>()
        val events = try { usageStatsManager.queryEvents(startTime, endTime) } catch (e: Exception) { null } ?: return emptyMap()
        
        val event = UsageEvents.Event()
        val startTimes = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            
            if (filterRealApps && !isRealUserApp(pkg)) continue

            val timestamp = event.timeStamp
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                startTimes[pkg] = Math.max(timestamp, startTime)
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                val start = startTimes[pkg]
                if (start != null && timestamp > start) {
                    stats[pkg] = (stats[pkg] ?: 0L) + (timestamp - start)
                }
                startTimes.remove(pkg)
            }
        }
        
        startTimes.forEach { (pkg, start) ->
            if (endTime > start) {
                stats[pkg] = (stats[pkg] ?: 0L) + (endTime - start)
            }
        }
        return stats
    }

    fun getDailyTotalsForLastWeek(): List<Long> {
        val totals = MutableList(7) { 0L }
        for (i in 0..6) {
            val stats = getTopAppsForDay(6 - i)
            totals[i] = stats.values.sum()
        }
        return totals
    }

    fun getTopAppsForDay(daysAgo: Int): Map<String, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val start = getStartOfDay(cal.timeInMillis)
        val end = if (daysAgo == 0) System.currentTimeMillis() else getEndOfDay(cal.timeInMillis)
        return getTimeByEvents(start, end, true)
    }

    fun isRealUserApp(packageName: String?): Boolean {
        if (packageName == null || packageName == context.packageName) return false
        if (packageName == "android" || packageName.contains("com.android.systemui")) return false
        return true
    }

    private fun getStartOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun getEndOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
}
