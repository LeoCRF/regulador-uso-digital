package com.example.regulador_uso_digital

import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class AppsActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private var adapter: AppLimitsAdapter? = null
    private var allAppsList = listOf<AppLimitInfo>()
    private var launchablePackages = setOf<String>()
    
    private var currentCategoryFilter = "TODOS"
    
    private val appCache = mutableMapOf<String, CachedAppInfo>()
    data class CachedAppInfo(val name: String, val icon: Drawable, val category: String)

    private val sharedPrefs by lazy { getSharedPreferences("app_limits", Context.MODE_PRIVATE) }

    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAppsData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        usageStatsHelper = UsageStatsHelper(this)
        recyclerView = findViewById(R.id.recycler_view_apps)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = AppLimitsAdapter(emptyList()) { updatedApp ->
            saveLimit(updatedApp)
        }
        recyclerView.adapter = adapter

        setupNavigation()
        setupFilters()
        setupResetButton()
        
        lifecycleScope.launch {
            indexLaunchableApps()
            refreshAppsData()
        }
    }

    private fun saveLimit(app: AppLimitInfo) {
        sharedPrefs.edit().apply {
            putInt("${app.packageName}_limit", app.currentLimitMinutes)
            putInt("${app.packageName}_simulated", app.simulatedAdjustment)
            apply()
        }
    }

    private fun setupResetButton() {
        findViewById<View>(R.id.btn_reset).setOnClickListener {
            sharedPrefs.edit().clear().apply()
            refreshAppsData()
            Toast.makeText(this, "Limites restaurados", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun indexLaunchableApps() {
        withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resInfos = packageManager.queryIntentActivities(intent, 0)
            launchablePackages = resInfos.map { it.activityInfo.packageName }.toSet()
        }
    }

    private fun setupNavigation() {
        // Botão Hoje (1º)
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        // Botão Semana (3º)
        findViewById<View>(R.id.nav_semana).setOnClickListener {
            val intent = Intent(this, SemanaActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }

        val devListener = View.OnClickListener {
            Toast.makeText(this, "Em breve em uma nova atualização!", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.nav_tips).setOnClickListener(devListener)
        findViewById<View>(R.id.nav_alertas).setOnClickListener(devListener)
    }

    private fun setupFilters() {
        val chipGroup: ChipGroup = findViewById(R.id.chip_group_filter)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentCategoryFilter = if (checkedIds.isEmpty()) {
                "TODOS"
            } else {
                findViewById<Chip>(checkedIds[0]).text.toString().uppercase()
            }
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        val filtered = if (currentCategoryFilter == "TODOS") {
            allAppsList
        } else {
            val tag = when(currentCategoryFilter) {
                "SOCIAL" -> "SOCIAL"
                "MENSAGENS" -> "MENSAGENS"
                "JOGOS" -> "JOGOS"
                else -> "ENTRETENIMENTO"
            }
            allAppsList.filter { it.category == tag }
        }
        adapter?.updateData(filtered)
    }

    private fun refreshAppsData() {
        lifecycleScope.launch {
            val appsList = withContext(Dispatchers.IO) { loadAppsDataAsync() }
            allAppsList = appsList
            applyCurrentFilter()
        }
    }

    private suspend fun loadAppsDataAsync(): List<AppLimitInfo> {
        val usageStats = usageStatsHelper.getUsageStatsLast24Hours()
        val pm = packageManager
        val appLimitList = mutableListOf<AppLimitInfo>()

        for (stat in usageStats) {
            val pkg = stat.packageName
            if (!launchablePackages.contains(pkg)) continue

            try {
                val cached = appCache[pkg] ?: run {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(ai).toString()
                    val icon = pm.getApplicationIcon(ai)
                    
                    val category = when {
                        pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messenger") || pkg.contains("message") -> "MENSAGENS"
                        pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("tiktok") || pkg.contains("twitter") || pkg.contains("social") -> "SOCIAL"
                        pkg.contains("game") || pkg.contains("clash") || pkg.contains("king") || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ai.category == ApplicationInfo.CATEGORY_GAME) -> "JOGOS"
                        else -> "ENTRETENIMENTO"
                    }
                    
                    val info = CachedAppInfo(appName, icon, category)
                    appCache[pkg] = info
                    info
                }

                if (stat.totalTimeInForeground > 0) {
                    val savedLimit = sharedPrefs.getInt("${pkg}_limit", 0)
                    val savedSimulated = sharedPrefs.getInt("${pkg}_simulated", 0)
                    val usageInMinutes = (stat.totalTimeInForeground / 60000).toInt()
                    val recommended = (usageInMinutes * 0.85).toInt()

                    appLimitList.add(AppLimitInfo(
                        pkg, cached.name, cached.category, formatTime(stat.totalTimeInForeground),
                        cached.icon, stat.totalTimeInForeground,
                        if (savedLimit > 0) savedLimit else recommended,
                        savedSimulated,
                        recommended
                    ))
                }
            } catch (e: Exception) { }
        }
        return appLimitList.sortedByDescending { it.usageMillis }
    }

    private fun formatTime(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, statsUpdateReceiver, IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS"), ContextCompat.RECEIVER_EXPORTED)
        refreshAppsData()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statsUpdateReceiver)
    }
}
