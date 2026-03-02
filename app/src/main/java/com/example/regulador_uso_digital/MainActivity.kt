package com.example.regulador_uso_digital

import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var barChart: BarChart
    private lateinit var averageTextView: TextView
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
        averageTextView = findViewById(R.id.average_usage_text)

        setupRecyclerView()
        setupInitialChart()
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
    }

    private fun setupInitialChart() {
        barChart.setDrawGridBackground(false)
        barChart.setDrawBarShadow(false)
        barChart.setDrawValueAboveBar(true)
        barChart.description.isEnabled = false
        barChart.setBackgroundColor(Color.WHITE)
        barChart.setGridBackgroundColor(Color.WHITE)
        barChart.setPinchZoom(false)
        barChart.setScaleEnabled(false)
        barChart.legend.isEnabled = false
        
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textColor = Color.parseColor("#636E72")
        xAxis.textSize = 10f

        val leftAxis = barChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#F0F2F8")
        leftAxis.textColor = Color.parseColor("#636E72")
        leftAxis.textSize = 10f
        leftAxis.axisMinimum = 0f
        
        barChart.axisRight.isEnabled = false
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
            val success = usageStatsHelper.requestUsagePermission()
            if (!success) {
                Toast.makeText(this, "Ative a permissão manualmente.", Toast.LENGTH_LONG).show()
            }
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

    private fun updateChartAndAverage() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        var totalUsageMillis = 0L
        val dayFormatter = SimpleDateFormat("EEE", Locale("pt", "BR"))

        for (i in 6 downTo 0) {
            val dayCalendar = Calendar.getInstance()
            dayCalendar.add(Calendar.DAY_OF_YEAR, -i)
            dayCalendar.set(Calendar.HOUR_OF_DAY, 0)
            dayCalendar.set(Calendar.MINUTE, 0)
            dayCalendar.set(Calendar.SECOND, 0)
            val startTime = dayCalendar.timeInMillis
            
            dayCalendar.set(Calendar.HOUR_OF_DAY, 23)
            dayCalendar.set(Calendar.MINUTE, 59)
            dayCalendar.set(Calendar.SECOND, 59)
            val endTime = dayCalendar.timeInMillis

            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            val dailyTotal = stats.sumOf { it.totalTimeInForeground }
            
            val hours = dailyTotal.toFloat() / (1000 * 60 * 60)
            entries.add(BarEntry((6 - i).toFloat(), hours))
            
            val dayName = dayFormatter.format(dayCalendar.time).replaceFirstChar { it.uppercase() }
            labels.add(dayName)
            
            totalUsageMillis += dailyTotal
        }

        val dataSet = BarDataSet(entries, "Horas")
        dataSet.color = Color.parseColor("#6C63FF")
        dataSet.valueTextColor = Color.parseColor("#2D3436")
        dataSet.valueTextSize = 10f
        
        val barData = BarData(dataSet)
        barData.barWidth = 0.6f
        
        barChart.data = barData
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        
        if (isFirstLoad) {
            barChart.animateY(1000)
            isFirstLoad = false
        }
        barChart.invalidate()

        val averageMillis = totalUsageMillis / 7
        val avgHours = averageMillis / (1000 * 60 * 60)
        val avgMinutes = (averageMillis / (1000 * 60)) % 60
        averageTextView.text = String.format(Locale.getDefault(), "Média diária (7 dias): %dh %02dm", avgHours, avgMinutes)
    }

    private fun displayUsageStats() {
        val usageStats = usageStatsHelper.getUsageStatsLast24Hours()
        val pm = packageManager

        val filteredUsageStats = usageStats.filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }

        val appUsageInfoList = mutableListOf<AppUsageInfo>()
        var totalDailyTime = 0L

        // Primeiro calcula o total para a barra de progresso
        for (stat in filteredUsageStats) {
             if (!stat.packageName.startsWith("com.android.providers") && 
                 !stat.packageName.startsWith("com.android.systemui") &&
                 !stat.packageName.contains("overlay")) {
                 totalDailyTime += stat.totalTimeInForeground
             }
        }

        for (stat in filteredUsageStats) {
            try {
                val ai = pm.getApplicationInfo(stat.packageName, 0)
                val appName = pm.getApplicationLabel(ai).toString()
                val appIcon = pm.getApplicationIcon(ai)
                
                if (!stat.packageName.startsWith("com.android.providers") && 
                    !stat.packageName.startsWith("com.android.systemui") &&
                    !stat.packageName.contains("overlay")) {
                    
                    val formattedTime = formatTime(stat.totalTimeInForeground)
                    // Agora passamos o tempo em milissegundos também
                    appUsageInfoList.add(AppUsageInfo(appName, formattedTime, appIcon, stat.totalTimeInForeground))
                }
                
            } catch (e: PackageManager.NameNotFoundException) {
                val defaultIcon = pm.defaultActivityIcon
                appUsageInfoList.add(AppUsageInfo(stat.packageName, formatTime(stat.totalTimeInForeground), defaultIcon, stat.totalTimeInForeground))
            }
        }
        // Atualiza o adaptador com a lista e o tempo total
        usageStatsAdapter.updateData(appUsageInfoList, totalDailyTime)
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
