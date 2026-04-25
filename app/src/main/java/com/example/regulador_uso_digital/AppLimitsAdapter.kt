package com.example.regulador_uso_digital

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppLimitInfo(
    val appName: String,
    val category: String,
    val usageTimeFormatted: String,
    val icon: Drawable,
    val usageMillis: Long,
    var currentLimitMinutes: Int = 0,
    var simulatedAdjustment: Int = 0
)

class AppLimitsAdapter(private var apps: List<AppLimitInfo>) : RecyclerView.Adapter<AppLimitsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val category: TextView = view.findViewById(R.id.app_category)
        val usageTime: TextView = view.findViewById(R.id.usage_time)
        val progressBar: ProgressBar = view.findViewById(R.id.usage_progress)
        val editLimit: EditText = view.findViewById(R.id.edit_limit)
        val btnMinus: ImageButton = view.findViewById(R.id.btn_minus)
        val btnPlus: ImageButton = view.findViewById(R.id.btn_plus)
        val simulatedValue: TextView = view.findViewById(R.id.simulated_value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.appName
        holder.category.text = app.category
        holder.usageTime.text = app.usageTimeFormatted
        holder.icon.setImageDrawable(app.icon)
        
        // Simular progresso (apenas visual por enquanto)
        holder.progressBar.progress = 75 

        // Configurar limite inicial (Recomendação: 15% a menos)
        if (app.currentLimitMinutes == 0) {
            app.currentLimitMinutes = ((app.usageMillis / (1000 * 60)) * 0.85).toInt()
        }
        holder.editLimit.setText(app.currentLimitMinutes.toString())

        // Simulação
        holder.simulatedValue.text = if (app.simulatedAdjustment >= 0) "+${app.simulatedAdjustment} min" else "${app.simulatedAdjustment} min"

        holder.btnPlus.setOnClickListener {
            app.simulatedAdjustment += 5
            notifyItemChanged(position)
        }

        holder.btnMinus.setOnClickListener {
            app.simulatedAdjustment -= 5
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = apps.size

    fun updateData(newApps: List<AppLimitInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}