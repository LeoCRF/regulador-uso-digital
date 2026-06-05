package com.example.regulador_uso_digital

import android.animation.ValueAnimator
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import kotlinx.coroutines.*
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

    private var shouldAnimateChart = true
    private var shouldAnimateCounters = true
    private val sharedPrefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

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

        setupRecyclerView()
        setupInitialChart()
        setupNavigation()
        setupMonitoringSwitch()
    }

    private fun setupNavigation() {
        val navClickListener = View.OnClickListener { v ->
            val intent = when(v.id) {
                R.id.nav_apps, R.id.btn_todos_apps -> Intent(this, AppsActivity::class.java)
                R.id.nav_semana, R.id.btn_ver_relatorio -> Intent(this, SemanaActivity::class.java)
                R.id.nav_tips -> Intent(this, DicasActivity::class.java)
                R.id.nav_alertas -> Intent(this, AlertasActivity::class.java)
                else -> null
            }
            intent?.let {
                startActivity(it)
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }

        findViewById<View>(R.id.nav_apps).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_semana).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_tips).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_alertas).setOnClickListener(navClickListener)
        
        // Novos botões de atalho no corpo da tela
        findViewById<View>(R.id.btn_ver_relatorio).setOnClickListener(navClickListener)
        findViewById<View>(R.id.btn_todos_apps).setOnClickListener(navClickListener)
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = false
    }

    private fun setupInitialChart() {
        barChart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setExtraOffsets(5f, 40f, 5f, 10f)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = "#9E9EBA".toColorInt()
                textSize = 10f
                granularity = 1f
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

    override fun onResume() {
        super.onResume()
        shouldAnimateChart = true
        shouldAnimateCounters = true

        val filter = IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS")
        // Correção da linha 167: Uso do ContextCompat para registro seguro no Android 14+
        ContextCompat.registerReceiver(
            this,
            statsUpdateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (usageStatsHelper.hasUsagePermission()) {
            if (sharedPrefs.getBoolean("monitoring_active", true)) startMonitoringService()
            refreshAllData(animateFlag = true)
        } else {
            usageStatsHelper.requestUsagePermission()
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statsUpdateReceiver) } catch (e: Exception) {}
    }

    private fun refreshAllData(animateFlag: Boolean) {
        lifecycleScope.launch {
            val usageMap = withContext(Dispatchers.IO) { usageStatsHelper.getUsageStatsToday() }
            val weeklyTotals = withContext(Dispatchers.IO) { usageStatsHelper.getDailyTotalsForLastWeek() }
            
            processAndDisplayUsage(usageMap, animateFlag)
            processAndDisplayChart(weeklyTotals, animateFlag)
        }
    }

    private fun animateTextCount(textView: TextView, targetValue: Long, isTime: Boolean = false, durationMillis: Long = 2500) {
        val animator = ValueAnimator.ofFloat(0f, targetValue.toFloat())
        animator.duration = durationMillis
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val actualValue = (animation.animatedValue as Float).toLong()
            textView.text = if (isTime) formatTime(actualValue) else actualValue.toString()
        }
        animator.start()
    }

    private suspend fun processAndDisplayUsage(usageMap: Map<String, Long>, animate: Boolean) {
        val totalToday = usageMap.values.sum()
        val sortedPkgs = usageMap.toList().sortedByDescending { it.second }

        val appUsageInfoList = withContext(Dispatchers.IO) {
            sortedPkgs
                .filter { it.second >= 1000 }
                .map { (pkg, time) ->
                    async {
                        val info = usageStatsHelper.getAppBasicInfo(pkg)
                        if (info != null) {
                            AppUsageInfo(info.name, formatTime(time), info.icon, time)
                        } else null
                    }
                }
                .awaitAll()
                .filterNotNull()
        }

        withContext(Dispatchers.Main) {
            if (animate && shouldAnimateCounters) {
                animateTextCount(totalTimeTextView, totalToday, true)
                animateTextCount(appsUsedCountTextView, appUsageInfoList.size.toLong())
                shouldAnimateCounters = false
            } else {
                totalTimeTextView.text = formatTime(totalToday)
                appsUsedCountTextView.text = appUsageInfoList.size.toString()
            }
            
            usageStatsAdapter.updateData(appUsageInfoList, totalToday)
            
            if (animate && appUsageInfoList.isNotEmpty()) {
                recyclerView.scheduleLayoutAnimation()
            }
        }
    }

    private suspend fun processAndDisplayChart(weeklyTotals: List<Long>, animate: Boolean) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val dayFormatter = SimpleDateFormat("EEE", Locale("pt", "BR"))
        
        var totalPreviousSum = 0L
        for (i in 0..6) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(6 - i))
            
            val totalMillis = if (i < weeklyTotals.size) weeklyTotals[i] else 0L
            entries.add(BarEntry(i.toFloat(), totalMillis.toFloat() / 3600000f))
            labels.add(dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() })
            
            if (i < 6) totalPreviousSum += totalMillis
        }

        withContext(Dispatchers.Main) {
            val dataSet = BarDataSet(entries, "").apply {
                color = Color.WHITE
                setDrawValues(true)
                valueTextColor = Color.WHITE
                valueTextSize = 9f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val totalMinutes = (value * 60).toInt()
                        if (totalMinutes <= 0) return ""
                        val h = totalMinutes / 60
                        val m = totalMinutes % 60
                        return if (h > 0) "${h}h ${m}m" else "${m}m"
                    }
                }
            }
            barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            
            if (animate && shouldAnimateChart) {
                barChart.animateY(8000, Easing.EaseOutQuart)
                shouldAnimateChart = false
            } else {
                barChart.invalidate()
            }

            val avg = if (totalPreviousSum > 0) totalPreviousSum / 6 else 0L
            weeklyAverageTextView.text = formatTime(avg)
            
            val todayUsage = if (weeklyTotals.isNotEmpty()) weeklyTotals.last() else 0L
            val diff = todayUsage - avg
            val sign = if (diff >= 0) "+" else "-"
            vsAverageTextView.text = "$sign${formatTime(abs(diff))} vs. média"
            vsAverageTextView.setTextColor(if (diff > 0) "#FFD600".toColorInt() else "#00E676".toColorInt())
        }
    }

    private fun formatTime(millis: Long): String {
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
