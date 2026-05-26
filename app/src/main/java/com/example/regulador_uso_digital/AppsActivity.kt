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
    
    // Fonte da verdade: contém absolutamente todos os apps com uso hoje
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
                return@setOnCheckedStateChangeListener
            }

            val checkedId = checkedIds[0]
            currentCategoryFilter = when (checkedId) {
                R.id.chip_social -> "SOCIAL"
                R.id.chip_entertainment -> "ENTRETENIMENTO"
                R.id.chip_games -> "JOGOS"
                else -> "TODOS"
            }
            applyCurrentFilter()
        }
    }

    private fun applyCurrentFilter() {
        // Sempre filtramos a partir da allAppsList original para garantir consistência
        val filtered = if (currentCategoryFilter == "TODOS") {
            allAppsList
        } else {
            allAppsList.filter { it.category == currentCategoryFilter }
        }
        
        // Passamos uma cópia da lista para o adapter
        adapter?.updateData(filtered.toList())
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
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

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

    private fun isRealUserApp(packageName: String): Boolean {
        val systemBlacklist = listOf(
            "com.android.systemui",
            "android.systemui",
            "com.google.android.inputmethod",
            "com.android.launcher",
            "com.android.settings"
        )
        return systemBlacklist.none { packageName.contains(it, ignoreCase = true) }
    }

    private suspend fun loadAppsDataAsync(): List<AppLimitInfo> {
        val usageMap = usageStatsHelper.getUsageStatsToday()
        val pm = packageManager
        val appLimitList = mutableListOf<AppLimitInfo>()

        for ((pkg, totalTime) in usageMap) {
            if (!isRealUserApp(pkg)) continue
            if (totalTime <= 0) continue

            try {
                val cached = appCache[pkg] ?: run {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(ai).toString()
                    val icon = pm.getApplicationIcon(ai)
                    val category = detectCategory(pkg, ai)
                    
                    val info = CachedAppInfo(appName, icon, category)
                    appCache[pkg] = info
                    info
                }

                val savedLimit = sharedPrefs.getInt("${pkg}_limit", 0)
                val savedSimulated = sharedPrefs.getInt("${pkg}_simulated", 0)
                val usageInMinutes = (totalTime / 60000).toInt()
                val recommended = (usageInMinutes * 0.85).toInt().coerceAtLeast(1)

                appLimitList.add(AppLimitInfo(
                    pkg, cached.name, cached.category, formatTime(totalTime),
                    cached.icon, totalTime,
                    if (savedLimit > 0) savedLimit else recommended,
                    savedSimulated,
                    recommended
                ))
            } catch (e: Exception) { }
        }
        return appLimitList.distinctBy { it.packageName }.sortedByDescending { it.usageMillis }
    }

    private fun detectCategory(pkg: String, ai: ApplicationInfo): String {
        val pkgLower = pkg.lowercase()
        
        // Prioridade 1: Keywords específicas (mais assertivo para apps conhecidos como YouTube)
        val entKeywords = listOf("youtube", "netflix", "twitch", "disney", "primevideo", "hbo", "spotify", "deezer", "music", "video", "tv", "globo", "crunchyroll", "starplus", "paramount", "vlc", "player")
        if (entKeywords.any { pkgLower.contains(it) }) return "ENTRETENIMENTO"

        val socialKeywords = listOf("instagram", "facebook", "tiktok", "twitter", "x.android", "linkedin", "social", "reddit", "pinterest", "snapchat", "kwai", "threads", "tumblr", "beal", "whatsapp", "telegram", "messenger", "discord", "slack")
        if (socialKeywords.any { pkgLower.contains(it) }) return "SOCIAL"

        val gameKeywords = listOf("game", "clash", "king", "candy", "roblox", "freefire", "pubg", "fortnite", "toca", "mojang", "minecraft", "supercell", "playrix", "rovio")
        if (gameKeywords.any { pkgLower.contains(it) }) return "JOGOS"

        // Prioridade 2: Categorias do Sistema Android (API 26+)
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
        try { unregisterReceiver(statsUpdateReceiver) } catch (e: Exception) {}
    }
}
