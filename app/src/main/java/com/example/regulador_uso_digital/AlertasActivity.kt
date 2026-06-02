package com.example.regulador_uso_digital

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AlertasActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alertas)

        recyclerView = findViewById(R.id.recycler_view_alerts)
        emptyStateText = findViewById(R.id.empty_state_text)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadAlerts()
        setupNavigation()
    }

    private fun loadAlerts() {
        val sharedPrefs = getSharedPreferences("app_limits", Context.MODE_PRIVATE)
        val alertsJson = sharedPrefs.getString("alerts_history", null)
        
        if (alertsJson != null) {
            val type = object : TypeToken<List<AlertLimit>>() {}.type
            val alerts: List<AlertLimit> = Gson().fromJson(alertsJson, type)
            
            if (alerts.isNotEmpty()) {
                recyclerView.adapter = AlertsAdapter(alerts.reversed()) // Mostrar mais recentes primeiro
                recyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE
            } else {
                showEmptyState()
            }
        } else {
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.VISIBLE
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.nav_apps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
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
    }
}
