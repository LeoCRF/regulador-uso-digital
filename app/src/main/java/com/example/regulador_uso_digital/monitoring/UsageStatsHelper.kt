package com.example.regulador_uso_digital.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.*
import kotlin.math.max
import kotlin.math.min

class UsageStatsHelper(private val context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

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
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getTimeByEvents(startTime: Long, endTime: Long): Map<String, Long> {
        val stats = mutableMapOf<String, Long>()
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        val startTimes = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val timestamp = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    startTimes[pkg] = max(timestamp, startTime)
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = startTimes[pkg] ?: startTime
                    val end = min(timestamp, endTime)
                    
                    if (end > start) {
                        stats[pkg] = (stats[pkg] ?: 0L) + (end - start)
                    }
                    startTimes.remove(pkg)
                }
            }
        }
        
        startTimes.forEach { (pkg, start) ->
            val duration = endTime - start
            if (duration > 0) {
                stats[pkg] = (stats[pkg] ?: 0L) + duration
            }
        }

        return stats
    }

    fun getUsageStatsToday(): Map<String, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return getTimeByEvents(cal.timeInMillis, end)
    }

    fun getUsageStatsLast24Hours(): List<UsageStats> {
        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = cal.timeInMillis
        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime) ?: emptyList()
    }
}
