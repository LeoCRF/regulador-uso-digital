package com.example.regulador_uso_digital

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import java.util.*

class AppsActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppLimitsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        usageStatsHelper = UsageStatsHelper(this)
        recyclerView = findViewById(R.id.recycler_view_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupNavigation()
        loadAppsData()
    }

    private fun setupNavigation() {
        val btnHome: View = findViewById(R.id.nav_home)
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }
    }

    private fun loadAppsData() {
        val usageStats = usageStatsHelper.getUsageStatsLast24Hours()
        val pm = packageManager
        val appLimitList = mutableListOf<AppLimitInfo>()

        val filteredStats = usageStats.filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }

        for (stat in filteredStats) {
            try {
                val ai = pm.getApplicationInfo(stat.packageName, 0)
                
                val isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!isSystemApp || (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    
                    val appName = pm.getApplicationLabel(ai).toString()
                    val icon = pm.getApplicationIcon(ai)
                    val timeFormatted = formatTime(stat.totalTimeInForeground)
                    
                    val category = when {
                        stat.packageName.contains("social") || stat.packageName.contains("instagram") || stat.packageName.contains("facebook") -> "SOCIAL"
                        stat.packageName.contains("game") || stat.packageName.contains("clash") || stat.packageName.contains("tft") -> "JOGOS"
                        else -> "ENTRETENIMENTO"
                    }

                    appLimitList.add(AppLimitInfo(
                        appName, 
                        category, 
                        timeFormatted, 
                        icon, 
                        stat.totalTimeInForeground
                    ))
                }
            } catch (e: PackageManager.NameNotFoundException) { }
        }

        adapter = AppLimitsAdapter(appLimitList)
        recyclerView.adapter = adapter
    }

    private fun formatTime(millis: Long): String {
        val hours = (millis / (1000 * 60 * 60))
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
