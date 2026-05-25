package com.example.regulador_uso_digital

import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SemanaActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var barChart: BarChart
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var listLabel: TextView

    private val appCache = mutableMapOf<String, CachedAppInfo>()
    private var launchablePackages = setOf<String>()
    
    data class CachedAppInfo(val name: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semana)

        usageStatsHelper = UsageStatsHelper(this)
        
        barChart = findViewById(R.id.semana_detailed_chart)
        recyclerView = findViewById(R.id.semana_recycler_view)
        listLabel = findViewById(R.id.semana_list_label)

        setupRecyclerView()
        setupInitialChart()
        setupNavigation()
        
        lifecycleScope.launch {
            indexLaunchableApps()
            val now = System.currentTimeMillis()
            updateChart(now)
            updateTopAppsForDay(0, now) 
        }
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
    }

    private suspend fun indexLaunchableApps() {
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            launchablePackages = resInfos.map { it.activityInfo.packageName }.toSet()
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        findViewById<View>(R.id.nav_apps).setOnClickListener {
            val intent = Intent(this, AppsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }
    }

    private fun setupInitialChart() {
        barChart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setExtraOffsets(5f, 30f, 5f, 30f)
            setTouchEnabled(true)
            isHighlightPerTapEnabled = true

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#9E9EBA")
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#222250")
                textColor = Color.parseColor("#9E9EBA")
                textSize = 10f
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}h"
                }
            }
            axisRight.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val dayIndex = it.x.toInt()
                        updateTopAppsForDay(6 - dayIndex, System.currentTimeMillis())
                    }
                }
                override fun onNothingSelected() {
                    updateTopAppsForDay(0, System.currentTimeMillis())
                }
            })
        }
    }

    private fun updateTopAppsForDay(daysAgo: Int, nowMs: Long) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { 
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            
            // Fim estrito: Se for hoje, limite é agora. Se for passado, limite é 23:59:59 do dia em questão.
            val end = if (daysAgo == 0) nowMs else {
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                cal.timeInMillis
            }

            val dayLimit = end - start
            val locale = Locale.forLanguageTag("pt-BR")
            val dayName = if (daysAgo == 0) "Hoje" else {
                SimpleDateFormat("EEEE", locale).format(cal.time).replaceFirstChar { it.uppercase() }
            }

            listLabel.text = "Top 3 aplicativos de $dayName"

            withContext(Dispatchers.IO) {
                val usageMap = usageStatsHelper.getTimeByEvents(start, end)
                processStatsForTop3(usageMap, dayLimit)
            }
        }
    }

    private suspend fun processStatsForTop3(usageMap: Map<String, Long>, limitTime: Long) {
        val filtered = usageMap.filter { launchablePackages.contains(it.key) && it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
        
        val top3Stats = filtered.take(3)
        val totalDaily = filtered.sumOf { it.second }.coerceAtMost(limitTime)
        
        val appUsageInfoList = top3Stats.mapNotNull { (pkg, time) ->
            val info = appCache[pkg] ?: fetchAppInfo(pkg)
            if (info != null) {
                val cleanTime = time.coerceAtMost(limitTime)
                AppUsageInfo(info.name, formatTime(cleanTime), info.icon, cleanTime)
            } else null
        }
        
        withContext(Dispatchers.Main) {
            usageStatsAdapter.updateData(appUsageInfoList, totalDaily)
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

    private fun updateChart(nowMs: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val entries = mutableListOf<BarEntry>()
            val labels = mutableListOf<String>()
            val locale = Locale.forLanguageTag("pt-BR")
            val dayFormatter = SimpleDateFormat("EEE", locale)

            for (i in 6 downTo 0) {
                val cal = Calendar.getInstance().apply { 
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_YEAR, -i)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                
                val end = if (i == 0) nowMs else {
                    cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }

                val dailyUsageMap = usageStatsHelper.getTimeByEvents(start, end)
                val dailyTotal = dailyUsageMap.filter { launchablePackages.contains(it.key) }.values.sum()
                
                // Trava de segurança por dia
                val dayLimit = if (i == 0) (nowMs - start) else 86400000L
                val cappedTotal = dailyTotal.coerceAtMost(dayLimit)
                
                entries.add(BarEntry((6 - i).toFloat(), cappedTotal.toFloat() / 3600000f))
                labels.add(dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() })
            }

            withContext(Dispatchers.Main) {
                val dataSet = BarDataSet(entries, "Horas").apply {
                    color = Color.WHITE
                    highLightColor = Color.parseColor("#3A86FF")
                    valueTextColor = Color.WHITE; valueTextSize = 10f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val totalMin = (value * 60).toInt()
                            return if (totalMin / 60 > 0) "${totalMin/60}h ${totalMin%60}m" else "${totalMin}m"
                        }
                    }
                }
                barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
                barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                barChart.animateY(1500, Easing.EaseOutQuart)
                barChart.invalidate()
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val h = millis / 3600000; val m = (millis % 3600000) / 60000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
