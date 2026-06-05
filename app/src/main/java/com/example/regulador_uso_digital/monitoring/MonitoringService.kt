package com.example.regulador_uso_digital.monitoring

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.regulador_uso_digital.AlertLimit
import com.example.regulador_uso_digital.MainActivity
import com.example.regulador_uso_digital.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MonitoringService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "MonitoringChannel"
    private val LIMIT_CHANNEL_ID = "LimitExceededChannel"
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val notifiedApps = mutableSetOf<String>()

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkLimits()
            sendBroadcast(Intent("com.example.regulador_uso_digital.UPDATE_STATS"))
            handler.postDelayed(this, 15000) 
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsHelper = UsageStatsHelper(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        handler.post(monitorRunnable)
        return START_STICKY
    }

    private fun checkLimits() {
        val sharedPrefs = getSharedPreferences("app_limits", Context.MODE_PRIVATE)
        val usageMap = usageStatsHelper.getUsageStatsToday()
        val pm = packageManager

        for ((pkg, totalTime) in usageMap) {
            // Ignora apps de sistema e blacklist (Configurações, etc)
            if (!usageStatsHelper.isRealUserApp(pkg)) continue

            val isNotifyEnabled = sharedPrefs.getBoolean("${pkg}_notify", false)
            if (!isNotifyEnabled) continue

            val limitMinutes = sharedPrefs.getInt("${pkg}_limit", 0)
            if (limitMinutes <= 0) continue

            val usageMinutes = (totalTime / 60000).toInt()

            if (usageMinutes >= limitMinutes) {
                if (!notifiedApps.contains(pkg)) {
                    try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        val appName = pm.getApplicationLabel(ai).toString()
                        sendLimitNotification(pkg, appName, usageMinutes, limitMinutes)
                        saveAlertToHistory(AlertLimit(appName, pkg, usageMinutes, limitMinutes, System.currentTimeMillis()))
                        notifiedApps.add(pkg)
                    } catch (e: Exception) {}
                }
            } else {
                notifiedApps.remove(pkg)
            }
        }
    }

    private fun saveAlertToHistory(alert: AlertLimit) {
        val sharedPrefs = getSharedPreferences("app_limits", Context.MODE_PRIVATE)
        val gson = Gson()
        val alertsJson = sharedPrefs.getString("alerts_history", null)
        val type = object : TypeToken<MutableList<AlertLimit>>() {}.type
        val alerts: MutableList<AlertLimit> = if (alertsJson != null) {
            gson.fromJson(alertsJson, type)
        } else {
            mutableListOf()
        }
        
        alerts.add(alert)
        if (alerts.size > 50) alerts.removeAt(0)
        
        sharedPrefs.edit().putString("alerts_history", gson.toJson(alerts)).apply()
    }

    private fun sendLimitNotification(pkg: String, appName: String, usage: Int, limit: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, pkg.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, LIMIT_CHANNEL_ID)
            .setContentTitle("Limite Atingido: $appName")
            .setContentText("Você usou $usage min. Seu limite era de $limit min.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(pkg.hashCode(), notification)
        } catch (e: SecurityException) {}
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoramento em execução")
            .setContentText("O app está registrando seu tempo de uso silenciosamente.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Monitoramento",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = "Usado para permitir o monitoramento em tempo real"
            manager?.createNotificationChannel(serviceChannel)

            val limitChannel = NotificationChannel(
                LIMIT_CHANNEL_ID,
                "Alertas de Limite de Uso",
                NotificationManager.IMPORTANCE_HIGH
            )
            limitChannel.description = "Notifica quando você atinge o tempo sugerido para um app"
            manager?.createNotificationChannel(limitChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
    }
}
