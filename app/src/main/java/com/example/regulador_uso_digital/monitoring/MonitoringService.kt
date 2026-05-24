package com.example.regulador_uso_digital.monitoring

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.regulador_uso_digital.MainActivity
import com.example.regulador_uso_digital.R

class MonitoringService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val CHANNEL_ID = "MonitoringChannel"
    private lateinit var usageStatsHelper: UsageStatsHelper

    private val monitorRunnable = object : Runnable {
        override fun run() {
            sendBroadcast(Intent("com.example.regulador_uso_digital.UPDATE_STATS"))
            handler.postDelayed(this, 15000) // Otimizado: 15 segundos
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsHelper = UsageStatsHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        
        handler.post(monitorRunnable)
        
        return START_STICKY
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
            .setPriority(NotificationCompat.PRIORITY_MIN) // Prioridade Mínima
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Monitoramento",
                NotificationManager.IMPORTANCE_LOW // Silencioso: não aparece o ícone no topo
            )
            serviceChannel.description = "Usado para permitir o monitoramento em tempo real"
            serviceChannel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
    }
}
