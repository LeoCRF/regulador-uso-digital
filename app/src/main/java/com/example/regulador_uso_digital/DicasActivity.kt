package com.example.regulador_uso_digital

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class DicasActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dicas)
        setupNavigation()
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
        findViewById<View>(R.id.nav_alertas).setOnClickListener {
            startActivity(Intent(this, AlertasActivity::class.java))
            finish()
        }
    }
}
