package com.example.regulador_uso_digital

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val usageStatsHelper = UsageStatsHelper(this)

        if (!usageStatsHelper.hasUsagePermission()) {
            usageStatsHelper.requestUsagePermission()
        } else {
            val usageStats = usageStatsHelper.getUsageStatsLast24Hours()

            for (stat in usageStats) {
                Log.d(
                    "Usage_APP",
                    "App: ${stat.packageName}, Time: ${stat.totalTimeInForeground}"
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
