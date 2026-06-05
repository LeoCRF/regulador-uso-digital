package com.example.regulador_uso_digital

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
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
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SemanaActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var barChart: BarChart
    private lateinit var recyclerView: RecyclerView
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var listLabel: TextView
    private var weeklyTotals: List<Long> = emptyList()

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
        
        refreshData()
    }

    private fun setupRecyclerView() {
        usageStatsAdapter = UsageStatsAdapter(emptyList(), 0L)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = usageStatsAdapter
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = false
    }

    private fun setupNavigation() {
        val navClickListener = View.OnClickListener { v ->
            val intent = when(v.id) {
                R.id.nav_home -> Intent(this, MainActivity::class.java)
                R.id.nav_apps -> Intent(this, AppsActivity::class.java)
                R.id.nav_tips -> Intent(this, DicasActivity::class.java)
                R.id.nav_alertas -> Intent(this, AlertasActivity::class.java)
                else -> null
            }
            intent?.let {
                startActivity(it)
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                finish()
            }
        }

        findViewById<View>(R.id.nav_home).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_apps).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_tips).setOnClickListener(navClickListener)
        findViewById<View>(R.id.nav_alertas).setOnClickListener(navClickListener)
    }

    private fun refreshData() {
        lifecycleScope.launch {
            weeklyTotals = withContext(Dispatchers.IO) {
                usageStatsHelper.getDailyTotalsForLastWeek()
            }
            updateChartUI(weeklyTotals)
            // Por padrão mostra o dia de hoje (índice 6 do gráfico, 0 dias atrás)
            updateTopAppsForDay(0, weeklyTotals.lastOrNull() ?: 0L)
        }
    }

    private fun setupInitialChart() {
        barChart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            setExtraOffsets(5f, 40f, 5f, 10f) // Espaço para valores no topo
            setTouchEnabled(true)
            
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

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val daysAgo = 6 - index
                        val dayTotal = if (index in weeklyTotals.indices) weeklyTotals[index] else 0L
                        updateTopAppsForDay(daysAgo, dayTotal)
                    }
                }
                override fun onNothingSelected() {
                    updateTopAppsForDay(0, weeklyTotals.lastOrNull() ?: 0L)
                }
            })
        }
    }

    private fun updateTopAppsForDay(daysAgo: Int, dayTotal: Long) {
        lifecycleScope.launch {
            val cal = Calendar.getInstance().apply { 
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            
            val dayName = if (daysAgo == 0) "Hoje" else {
                SimpleDateFormat("EEEE", Locale("pt", "BR")).format(cal.time).replaceFirstChar { it.uppercase() }
            }
            
            val dateStr = SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(cal.time)
            listLabel.text = "Top 3 aplicativos de $dayName ($dateStr)"

            val appList = withContext(Dispatchers.IO) {
                val usageMap = usageStatsHelper.getTopAppsForDay(daysAgo)
                
                usageMap.toList()
                    .sortedByDescending { it.second }
                    .take(3)
                    .mapNotNull { (pkg, time) ->
                        val info = usageStatsHelper.getAppBasicInfo(pkg)
                        if (info != null && time > 0) {
                            AppUsageInfo(info.name, formatTime(time), info.icon, time)
                        } else null
                    }
            }
            
            usageStatsAdapter.updateData(appList, if (dayTotal > 0) dayTotal else appList.sumOf { it.timeMillis })
            recyclerView.scheduleLayoutAnimation()
        }
    }

    private fun updateChartUI(totals: List<Long>) {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val dayFormatter = SimpleDateFormat("EEE", Locale("pt", "BR"))

        for (i in 0..6) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(6 - i))
            
            val valueHours = totals[i].toFloat() / 3600000f
            entries.add(BarEntry(i.toFloat(), valueHours))
            labels.add(dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() })
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.WHITE
            highLightColor = "#3A86FF".toColorInt()
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
        barChart.animateY(1000, Easing.EaseOutQuart)
        barChart.invalidate()
    }

    private fun formatTime(millis: Long): String {
        val h = millis / 3600000
        val m = (millis % 3600000) / 60000
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
