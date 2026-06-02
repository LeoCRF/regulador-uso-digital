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
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsActivity : AppCompatActivity() {

    private lateinit var usageStatsHelper: UsageStatsHelper
    private lateinit var recyclerView: RecyclerView
    private var adapter: AppLimitsAdapter? = null

    // Fonte da verdade: apps que tiveram uso hoje
    private var allAppsList = listOf<AppLimitInfo>()
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

        refreshAppsData()
    }

    private fun setupFilters() {
        val chipGroup: ChipGroup = findViewById(R.id.chip_group_filter)
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                group.check(R.id.chip_all)
                currentCategoryFilter = "TODOS"
            } else {
                val checkedId = checkedIds[0]
                currentCategoryFilter = when (checkedId) {
                    R.id.chip_social -> "SOCIAL"
                    R.id.chip_entertainment -> "ENTRETENIMENTO"
                    R.id.chip_games -> "JOGOS"
                    R.id.chip_all -> "TODOS"
                    else -> "TODOS"
                }
            }
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        // Filtra a lista base que contém APENAS apps abertos
        val filtered = if (currentCategoryFilter == "TODOS") {
            allAppsList
        } else {
            allAppsList.filter { it.category == currentCategoryFilter }
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

    private fun saveLimit(app: AppLimitInfo) {
        sharedPrefs.edit().apply {
            putInt("${app.packageName}_limit", app.currentLimitMinutes)
            putInt("${app.packageName}_simulated", app.simulatedAdjustment)
            putBoolean("${app.packageName}_notify", app.isNotificationEnabled)
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

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.nav_semana).setOnClickListener {
            val intent = Intent(this, SemanaActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.nav_tips).setOnClickListener {
            val intent = Intent(this, DicasActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<View>(R.id.nav_alertas).setOnClickListener {
            val intent = Intent(this, AlertasActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun isRealUserApp(packageName: String): Boolean {
        val systemBlacklist = listOf(
            "com.android.systemui", "android.systemui", "com.google.android.inputmethod",
            "com.android.launcher", "com.android.settings", "com.google.android.googlequicksearchbox"
        )
        return systemBlacklist.none { packageName.contains(it, ignoreCase = true) }
    }

    private suspend fun loadAppsDataAsync(): List<AppLimitInfo> {
        val usageMap = usageStatsHelper.getUsageStatsToday()
        val pm = packageManager
        val appLimitList = mutableListOf<AppLimitInfo>()

        for ((pkg, totalTime) in usageMap) {
            // Mantemos o filtro de tempo para mostrar apenas o que foi usado
            if (totalTime <= 0) continue
            if (!isRealUserApp(pkg)) continue

            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                if (pm.getLaunchIntentForPackage(pkg) == null) continue

                val cached = appCache[pkg] ?: run {
                    val appName = pm.getApplicationLabel(ai).toString()
                    val icon = pm.getApplicationIcon(ai)
                    val category = detectCategory(pkg, ai)
                    val info = CachedAppInfo(appName, icon, category)
                    appCache[pkg] = info
                    info
                }

                val savedLimit = sharedPrefs.getInt("${pkg}_limit", 0)
                val savedSimulated = sharedPrefs.getInt("${pkg}_simulated", 0)
                val savedNotify = sharedPrefs.getBoolean("${pkg}_notify", false)
                val usageInMinutes = (totalTime / 60000).toInt()
                val recommended = (usageInMinutes * 0.85).toInt().coerceAtLeast(1)

                appLimitList.add(AppLimitInfo(
                    pkg, cached.name, cached.category, formatTime(totalTime),
                    cached.icon, totalTime,
                    if (savedLimit > 0) savedLimit else recommended,
                    savedSimulated,
                    recommended,
                    savedNotify
                ))
            } catch (e: Exception) { }
        }

        return appLimitList
            .distinctBy { it.packageName }
            .sortedByDescending { it.usageMillis }
    }

    private fun detectCategory(pkg: String, ai: ApplicationInfo): String {
        val pkgLower = pkg.lowercase()
        val entKeywords = listOf("youtube", "netflix", "twitch", "disney", "primevideo", "hbo", "spotify", "deezer", "music", "video", "tv", "globo", "crunchyroll", "starplus", "paramount", "vlc", "player", "tiktok")
        if (entKeywords.any { pkgLower.contains(it) }) return "ENTRETENIMENTO"

        val socialKeywords = listOf("instagram", "facebook", "twitter", "x.android", "linkedin", "social", "reddit", "pinterest", "snapchat", "kwai", "threads", "tumblr", "beal", "whatsapp", "telegram", "messenger", "discord", "slack")
        if (socialKeywords.any { pkgLower.contains(it) }) return "SOCIAL"

        val gameKeywords = listOf("game", "clash", "king", "candy", "roblox", "freefire", "pubg", "fortnite", "toca", "mojang", "minecraft", "supercell", "playrix", "rovio")
        if (gameKeywords.any { pkgLower.contains(it) }) return "JOGOS"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_GAME -> return "JOGOS"
                ApplicationInfo.CATEGORY_SOCIAL -> return "SOCIAL"
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> return "ENTRETENIMENTO"
            }
        }
        return "OUTROS"
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "0m"
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter("com.example.regulador_uso_digital.UPDATE_STATS")
            ContextCompat.registerReceiver(this, statsUpdateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {}
        refreshAppsData()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statsUpdateReceiver) } catch (e: Exception) {}
    }
}
