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
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.regulador_uso_digital.monitoring.UsageStatsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*

class AppsActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var switchSystemApps: SwitchCompat
    private var adapter: AppLimitsAdapter? = null

    private var allAppsList = listOf<AppLimitInfo>()
    private var isFirstLoad = true 
    private var currentLoadJob: Job? = null

    private val appCache = mutableMapOf<String, CachedAppInfo>()
    data class CachedAppInfo(val name: String, val icon: Drawable, val category: String)

    private val limitPrefs by lazy { getSharedPreferences("app_limits", Context.MODE_PRIVATE) }
    private val globalPrefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }

    private val statsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshAppsData(animate = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        usageStatsHelper = UsageStatsHelper(this)
        recyclerView = findViewById(R.id.recycler_view_apps)
        switchSystemApps = findViewById(R.id.switch_system_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppLimitsAdapter(emptyList()) { updatedApp ->
            saveLimit(updatedApp)
        }
        recyclerView.adapter = adapter

        setupNavigation()
        setupResetButton()
        setupSwitch()
    }

    private fun setupSwitch() {
        val showSystem = globalPrefs.getBoolean("show_system_apps", false)
        switchSystemApps.isChecked = showSystem
        switchSystemApps.setOnCheckedChangeListener { _, isChecked ->
            globalPrefs.edit { putBoolean("show_system_apps", isChecked) }
            refreshAppsData(animate = true)
        }
    }

    private fun refreshAppsData(animate: Boolean) {
        currentLoadJob?.cancel()
        currentLoadJob = lifecycleScope.launch {
            val appsList = withContext(Dispatchers.IO) { loadAppsDataAsync() }
            if (!isActive) return@launch
            allAppsList = appsList
            adapter?.updateData(allAppsList)
            if (animate && appsList.isNotEmpty()) {
                delay(200)
                recyclerView.scheduleLayoutAnimation()
            }
        }
    }

    private fun saveLimit(app: AppLimitInfo) {
        limitPrefs.edit().apply {
            putInt("${app.packageName}_limit", app.currentLimitMinutes)
            putInt("${app.packageName}_simulated", app.simulatedAdjustment)
            putBoolean("${app.packageName}_notify", app.isNotificationEnabled)
            apply()
        }
    }

    private fun setupResetButton() {
        findViewById<View>(R.id.btn_reset).setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
                .setTitle("Restaurar Padrões")
                .setMessage("Deseja voltar todos os apps para a meta recomendada de 15% de redução?")
                .setPositiveButton("Restaurar") { _, _ ->
                    limitPrefs.edit().clear().apply()
                    refreshAppsData(animate = true)
                    Toast.makeText(this, "Limites restaurados", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.nav_semana).setOnClickListener {
            startActivity(Intent(this, SemanaActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.nav_tips).setOnClickListener {
            startActivity(Intent(this, DicasActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.nav_alertas).setOnClickListener {
            startActivity(Intent(this, AlertasActivity::class.java))
            finish()
        }
    }

    private suspend fun loadAppsDataAsync(): List<AppLimitInfo> {
        val showSystem = globalPrefs.getBoolean("show_system_apps", false)
        val filterRealApps = !showSystem

        val weeklyUsageMap = usageStatsHelper.getUsageStatsWeekly(filterRealApps = filterRealApps)
        val todayUsageMap = usageStatsHelper.getUsageStatsToday(filterRealApps = filterRealApps)
        val pm = packageManager
        val appLimitList = mutableListOf<AppLimitInfo>()

        for ((pkg, totalWeeklyTime) in weeklyUsageMap) {
            if (totalWeeklyTime <= 0) continue
            val todayTime = todayUsageMap[pkg] ?: 0L
            val dailyAverageMinutes = (totalWeeklyTime / (7 * 60000)).toInt()
            val healthyRecommendedLimit = (dailyAverageMinutes * 0.85).toInt().coerceAtLeast(5)
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val cached = appCache[pkg] ?: run {
                    val appName = pm.getApplicationLabel(ai).toString()
                    val icon = pm.getApplicationIcon(ai)
                    val category = detectCategory(pkg, ai)
                    val info = CachedAppInfo(appName, icon, category)
                    appCache[pkg] = info
                    info
                }
                val savedLimit = limitPrefs.getInt("${pkg}_limit", 0)
                val savedSimulated = limitPrefs.getInt("${pkg}_simulated", 0)
                val savedNotify = limitPrefs.getBoolean("${pkg}_notify", false)
                appLimitList.add(AppLimitInfo(
                    pkg, cached.name, cached.category, formatTime(totalWeeklyTime),
                    cached.icon, todayTime, totalWeeklyTime, dailyAverageMinutes,
                    if (savedLimit > 0) savedLimit else healthyRecommendedLimit,
                    savedSimulated, healthyRecommendedLimit, savedNotify
                ))
            } catch (e: Exception) { 
                val fallbackName = pkg.split(".").lastOrNull() ?: pkg
                val fallbackIcon = pm.defaultActivityIcon
                val savedLimit = limitPrefs.getInt("${pkg}_limit", 0)
                val savedSimulated = limitPrefs.getInt("${pkg}_simulated", 0)
                val savedNotify = limitPrefs.getBoolean("${pkg}_notify", false)
                appLimitList.add(AppLimitInfo(
                    pkg, fallbackName, "SISTEMA", formatTime(totalWeeklyTime),
                    fallbackIcon, todayTime, totalWeeklyTime, dailyAverageMinutes,
                    if (savedLimit > 0) savedLimit else healthyRecommendedLimit,
                    savedSimulated, healthyRecommendedLimit, savedNotify
                ))
            }
        }
        return appLimitList.distinctBy { it.packageName }.sortedByDescending { it.weeklyUsageMillis }
    }

    private fun detectCategory(pkg: String, ai: ApplicationInfo): String {
        val pkgLower = pkg.lowercase()
        if (listOf("youtube", "netflix", "twitch", "disney", "primevideo", "hbo", "spotify", "deezer", "music", "video", "tv", "globo", "crunchyroll", "starplus", "paramount", "vlc", "player", "tiktok").any { pkgLower.contains(it) }) return "ENTRETENIMENTO"
        if (listOf("instagram", "facebook", "twitter", "x.android", "linkedin", "social", "reddit", "pinterest", "snapchat", "kwai", "threads", "tumblr", "beal", "whatsapp", "telegram", "messenger", "discord", "slack").any { pkgLower.contains(it) }) return "SOCIAL"
        if (listOf("game", "clash", "king", "candy", "roblox", "freefire", "pubg", "fortnite", "toca", "mojang", "minecraft", "supercell", "playrix", "rovio").any { pkgLower.contains(it) }) return "JOGOS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_GAME -> return "JOGOS"
                ApplicationInfo.CATEGORY_SOCIAL -> return "SOCIAL"
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> return "ENTRETENIMENTO"
            }
        }
        return "GERAL"
    }

    private fun formatTime(millis: Long): String {
        val totalMinutes = (millis / 60000).toInt()
        if (totalMinutes <= 0) return "${(millis / 1000).toInt()}s"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m} min"
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS")
            ContextCompat.registerReceiver(this, statsUpdateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {}
        refreshAppsData(animate = isFirstLoad)
        isFirstLoad = false
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statsUpdateReceiver) } catch (e: Exception) {}
    }
}
