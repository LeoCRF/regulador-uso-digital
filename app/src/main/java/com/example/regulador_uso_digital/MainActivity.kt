package com.example.regulador_uso_digital

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usageStatsHelper = UsageStatsHelper(this)
        recyclerView = findViewById(R.id.recycler_view)

        // Setup RecyclerView
        usageStatsAdapter = UsageStatsAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
    }

    override fun onResume() {
        super.onResume()

        if (!usageStatsHelper.hasUsagePermission()) {
            val success = usageStatsHelper.requestUsagePermission()
            if (!success) {
                Toast.makeText(
                    this,
                    "Falha ao abrir a tela de permissão. Por favor, ative manualmente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            displayUsageStats()
        }
    }

    private fun displayUsageStats() {
        val usageStats = usageStatsHelper.getUsageStatsLast24Hours()
        val pm = packageManager

        val sortedStats = usageStats.sortedByDescending { it.totalTimeInForeground }
        val appUsageInfoList = mutableListOf<AppUsageInfo>()

        for (stat in sortedStats) {
            if (stat.totalTimeInForeground > 0) {
                try {
                    if (pm.getLaunchIntentForPackage(stat.packageName) != null) {
                        val ai = pm.getApplicationInfo(stat.packageName, 0)
                        val appName = pm.getApplicationLabel(ai).toString()
                        val formattedTime = formatTime(stat.totalTimeInForeground)
                        appUsageInfoList.add(AppUsageInfo(appName, formattedTime))
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    // App might be uninstalled, ignore
                }
            }
        }
        usageStatsAdapter.updateData(appUsageInfoList)
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
