package com.example.regulador_uso_digital

import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.MonitoringService
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var barChart: BarChart
    private lateinit var totalTimeTextView: TextView
    private lateinit var vsAverageTextView: TextView
    private lateinit var appsUsedCountTextView: TextView
    private lateinit var weeklyAverageTextView: TextView
    private var isFirstLoad = true

    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            displayUsageStats()
            updateChartAndAverage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usageStatsHelper = UsageStatsHelper(this)
        recyclerView = findViewById(R.id.recycler_view)
        barChart = findViewById(R.id.usage_chart)
        totalTimeTextView = findViewById(R.id.total_time_text)
        vsAverageTextView = findViewById(R.id.vs_average_text)
        appsUsedCountTextView = findViewById(R.id.apps_used_count)
        weeklyAverageTextView = findViewById(R.id.weekly_average_text)

        setupRecyclerView()
        setupInitialChart()
        setupNavigation()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_apps).setOnClickListener {
            val intent = Intent(this, AppsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
    }

    private fun setupInitialChart() {
        barChart.apply {
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setExtraOffsets(5f, 30f, 5f, 30f) 
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                textColor = Color.parseColor("#9E9EBA")
                textSize = 11f
                yOffset = 0f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#222250")
                setDrawAxisLine(false)
                textColor = Color.parseColor("#9E9EBA")
                textSize = 10f
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}h"
                    }
                }
            }
            
            axisRight.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, 
            statsUpdateReceiver, 
            IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS"), 
            ContextCompat.RECEIVER_EXPORTED
        )

        if (!usageStatsHelper.hasUsagePermission()) {
            usageStatsHelper.requestUsagePermission()
        } else {
            startMonitoringService()
            displayUsageStats()
            updateChartAndAverage()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statsUpdateReceiver)
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun isRealApp(packageName: String): Boolean {
        return try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            (!isSystem || isUpdatedSystem) && 
                    !packageName.contains("android.systemui") && 
                    !packageName.contains("android.providers") &&
                    !packageName.contains("com.google.android.inputmethod")
        } catch (e: Exception) { false }
    }

    private fun updateChartAndAverage() {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        var totalPreviousUsage = 0L
        val dayFormatter = SimpleDateFormat("EEE", Locale("pt", "BR"))

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
            val end = cal.timeInMillis

            val stats = usageStatsHelper.getUsageStatsRange(start, end)
            val dailyTotal = stats.filter { isRealApp(it.packageName) }.sumOf { it.totalTimeInForeground }
            
            val hoursValue = dailyTotal.toFloat() / 3600000f
            entries.add(BarEntry((6 - i).toFloat(), hoursValue))
            
            val dayName = dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() }
            labels.add(dayName)
            
            if (i > 0) totalPreviousUsage += dailyTotal
        }

        val dataSet = BarDataSet(entries, "Horas").apply {
            color = Color.WHITE
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value <= 0.05f) return "" 
                    val totalMinutes = (value * 60).toInt()
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60
                    return if (h > 0) "${h}h ${m}m" else "${m}m"
                }
            }
        }
        
        barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        
        if (isFirstLoad) { 
            barChart.animateY(1200) // Animação um pouco mais longa para ser notada
            isFirstLoad = false 
        }
        barChart.invalidate()

        val avgMillis = totalPreviousUsage / 6
        val totalTodayMillis = (entries.last().y * 3600000).toLong()
        
        weeklyAverageTextView.text = formatTime(avgMillis)
        
        val diff = totalTodayMillis - avgMillis
        val diffText = (if (diff >= 0) "+" else "-") + formatTime(Math.abs(diff)) + " vs. média"
        vsAverageTextView.text = diffText
        vsAverageTextView.setTextColor(if (diff > 0) Color.parseColor("#FFD600") else Color.parseColor("#00E676"))
    }

    private fun displayUsageStats() {
        val usageStats = usageStatsHelper.getUsageStatsLast24Hours()
        val appUsageInfoList = mutableListOf<AppUsageInfo>()
        var totalToday = 0L

        val filteredStats = usageStats.filter { it.totalTimeInForeground > 0 && isRealApp(it.packageName) }
            .sortedByDescending { it.totalTimeInForeground }

        for (stat in filteredStats) {
            try {
                val ai = packageManager.getApplicationInfo(stat.packageName, 0)
                appUsageInfoList.add(AppUsageInfo(
                    packageManager.getApplicationLabel(ai).toString(),
                    formatTime(stat.totalTimeInForeground),
                    packageManager.getApplicationIcon(ai),
                    stat.totalTimeInForeground
                ))
                totalToday += stat.totalTimeInForeground
            } catch (e: Exception) { }
        }
        
        totalTimeTextView.text = formatTime(totalToday)
        appsUsedCountTextView.text = appUsageInfoList.size.toString()
        usageStatsAdapter.updateData(appUsageInfoList, totalToday)
    }

    private fun formatTime(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
