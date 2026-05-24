package com.example.regulador_uso_digital

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class SemanaActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var barChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semana)

        usageStatsHelper = UsageStatsHelper(this)
        barChart = findViewById(R.id.semana_detailed_chart)

        setupInitialChart()
        setupNavigation()
        updateChart()
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        findViewById<View>(R.id.nav_apps).setOnClickListener {
            val intent = Intent(this, AppsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
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
        }
    }

    private fun updateChart() {
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
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
            // Filtro simplificado para exemplo, idealmente usaria isRealUserApp se acessível
            val dailyTotal = stats.sumOf { it.totalTimeInForeground }
            val cappedTotal = Math.min(dailyTotal, 86400000L)
            
            entries.add(BarEntry((6 - i).toFloat(), cappedTotal.toFloat() / 3600000f))
            labels.add(dayFormatter.format(cal.time).replaceFirstChar { it.uppercase() })
        }

        val dataSet = BarDataSet(entries, "Horas").apply {
            color = Color.WHITE
            valueTextColor = Color.WHITE
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val totalMin = (value * 60).toInt()
                    return if (totalMin / 60 > 0) "${totalMin/60}h ${totalMin%60}m" else "${totalMin}m"
                }
            }
        }
        
        barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.animateY(2000, Easing.EaseOutQuart)
        barChart.invalidate()
    }
}
