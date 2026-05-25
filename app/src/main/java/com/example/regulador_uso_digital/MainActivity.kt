package com.example.regulador_uso_digital

import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.MonitoringService
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var barChart: BarChart
    private lateinit var totalTimeTextView: TextView
    private lateinit var vsAverageTextView: TextView
    private lateinit var appsUsedCountTextView: TextView
    private lateinit var weeklyAverageTextView: TextView
    private lateinit var switchMonitoring: SwitchCompat
    private lateinit var appsListLabel: TextView

    private var shouldAnimateChart = true
    private val appCache = mutableMapOf<String, CachedAppInfo>()
    private var launchablePackages = setOf<String>()

    private val sharedPrefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    data class CachedAppInfo(val name: String, val icon: Drawable)

    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAllData(animateFlag = false)
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
        switchMonitoring = findViewById(R.id.switch_monitoring)
        appsListLabel = findViewById(R.id.apps_list_label)

        setupRecyclerView()
        setupInitialChart()
        setupNavigation()
        setupMonitoringSwitch()
        indexLaunchableApps()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_apps).setOnClickListener {
            val intent = Intent(this, AppsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        findViewById<View>(R.id.nav_semana).setOnClickListener {
            val intent = Intent(this, SemanaActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
        val animation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down)
        recyclerView.layoutAnimation = animation
    }

    private fun setupInitialChart() {
        barChart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setExtraOffsets(5f, 30f, 5f, 30f)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = "#9E9EBA".toColorInt()
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = "#222250".toColorInt()
                textColor = "#9E9EBA".toColorInt()
                textSize = 10f
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}h"
                }
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupMonitoringSwitch() {
        val isMonitoring = sharedPrefs.getBoolean("monitoring_active", true)
        switchMonitoring.isChecked = isMonitoring
        switchMonitoring.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit { putBoolean("monitoring_active", isChecked) }
            if (isChecked) startMonitoringService() else stopMonitoringService()
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopMonitoringService() {
        stopService(Intent(this, MonitoringService::class.java))
    }

    private fun indexLaunchableApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            launchablePackages = resInfos.map { it.activityInfo.packageName }.toSet()
            withContext(Dispatchers.Main) { refreshAllData(animateFlag = true) }
        }
    }

    override fun onResume() {
        super.onResume()
        shouldAnimateChart = true
        ContextCompat.registerReceiver(this, statsUpdateReceiver, IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS"), ContextCompat.RECEIVER_EXPORTED)

        if (usageStatsHelper.hasUsagePermission()) {
            if (sharedPrefs.getBoolean("monitoring_active", true)) startMonitoringService()
            refreshAllData(animateFlag = true)
        } else {
            usageStatsHelper.requestUsagePermission()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statsUpdateReceiver)
    }

    private fun refreshAllData(animateFlag: Boolean) {
        val now = System.currentTimeMillis()
        
        lifecycleScope.launch {
            launch(Dispatchers.IO) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startToday = cal.timeInMillis
                val usageMap = usageStatsHelper.getTimeByEvents(startToday, now)
                processAndDisplayUsage(usageMap, animateFlag, now, startToday)
            }
            launch(Dispatchers.IO) { processAndDisplayChart(animateFlag, now) }
        }
    }

    private fun isRealUserApp(packageName: String): Boolean {
        return launchablePackages.contains(packageName) && 
               !packageName.contains("android.systemui") && 
               !packageName.contains("com.android.launcher")
    }

    private suspend fun processAndDisplayUsage(usageMap: Map<String, Long>, animateList: Boolean, nowMs: Long, startTodayMs: Long) {
        val appUsageInfoList = mutableListOf<AppUsageInfo>()
        var totalToday = 0L

        val sortedPkgs = usageMap.keys.filter { (usageMap[it] ?: 0L) > 0 && isRealUserApp(it) }
            .sortedByDescending { usageMap[it] ?: 0L }

        for (pkg in sortedPkgs) {
            val appInfo = appCache[pkg] ?: fetchAppInfo(pkg)
            if (appInfo != null) {
                val time = usageMap[pkg] ?: 0L
                appUsageInfoList.add(AppUsageInfo(appInfo.name, formatTime(time), appInfo.icon, time))
                totalToday += time
            }
        }

        withContext(Dispatchers.Main) {
            val limitTime = nowMs - startTodayMs
            val finalTotal = totalToday.coerceAtMost(limitTime)
            
            totalTimeTextView.text = formatTime(finalTotal)
            appsUsedCountTextView.text = appUsageInfoList.size.toString()
            usageStatsAdapter.updateData(appUsageInfoList, finalTotal)
            appsListLabel.text = "Apps mais usados hoje"
            if (animateList && shouldAnimateChart) recyclerView.scheduleLayoutAnimation()
        }
    }

    private fun fetchAppInfo(packageName: String): CachedAppInfo? {
        return try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            val info = CachedAppInfo(packageManager.getApplicationLabel(ai).toString(), packageManager.getApplicationIcon(ai))
            appCache[packageName] = info
            info
        } catch (e: Exception) { null }
    }

    private suspend fun processAndDisplayChart(animate: Boolean, nowMs: Long) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        var totalPreviousUsage = 0L
        val dayFormatter = SimpleDateFormat("EEE", Locale.forLanguageTag("pt-BR"))

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            
            val end = if (i == 0) {
                nowMs
            } else {
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }

            val dailyUsageMap = usageStatsHelper.getTimeByEvents(start, end)
            val dailyTotal = dailyUsageMap.filter { isRealUserApp(it.key) }.values.sum()
            
            val dayLimit = if (i == 0) (nowMs - start) else 86400000L
            val cappedTotal = dailyTotal.coerceAtMost(dayLimit)
            
            entries.add(BarEntry((6 - i).toFloat(), cappedTotal.toFloat() / 3600000f))
            labels.add(dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() })

            if (i > 0) totalPreviousUsage += cappedTotal
        }

        withContext(Dispatchers.Main) {
            val dataSet = BarDataSet(entries, "Horas").apply {
                color = Color.WHITE
                valueTextColor = Color.WHITE; valueTextSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val totalMin = (value * 60).toInt()
                        return if (totalMin / 60 > 0) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"
                    }
                }
            }
            barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            if (animate && shouldAnimateChart) {
                barChart.animateY(2000, Easing.EaseOutQuart)
                shouldAnimateChart = false
            } else barChart.invalidate()

            val avgMillis = if (totalPreviousUsage > 0) totalPreviousUsage / 6 else 0L
            weeklyAverageTextView.text = formatTime(avgMillis)
            val totalTodayMillis = (entries.last().y * 3600000).toLong()
            val diff = totalTodayMillis - avgMillis
            
            val diffSign = if (diff >= 0) "+" else "-"
            vsAverageTextView.text = "$diffSign${formatTime(abs(diff))} vs. média"
            vsAverageTextView.setTextColor(if (diff > 0) "#FFD600".toColorInt() else "#00E676".toColorInt())
        }
    }

    private fun formatTime(millis: Long): String {
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
