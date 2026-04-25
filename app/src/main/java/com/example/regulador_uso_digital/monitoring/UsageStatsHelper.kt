package com.example.regulador_uso_digital.monitoring

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.*

class UsageStatsHelper (private val context: Context) {
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
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun getUsageStatsRange(startTime: Long, endTime: Long): List<UsageStats> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // queryAndAggregateUsageStats evita a duplicação de tempo por pacote
        val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        return statsMap.values.toList()
    }

    fun getUsageStatsLast24Hours(): List<UsageStats> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        // Início do dia atual (meia-noite) para o tempo "Hoje" ser real
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        return getUsageStatsRange(startTime, endTime)
    }
}
