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
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

data class AppLimitInfo(
    val packageName: String,
    val appName: String,
    val category: String,
    val usageTimeFormatted: String,
    val icon: Drawable,
    val usageMillis: Long,
    var currentLimitMinutes: Int = 0,
    var simulatedAdjustment: Int = 0,
    var recommendedLimit: Int = 0,
    var isNotificationEnabled: Boolean = false
)

class AppLimitsAdapter(
    private var apps: List<AppLimitInfo>,
    private val onLimitChanged: (AppLimitInfo) -> Unit
) : RecyclerView.Adapter<AppLimitsAdapter.ViewHolder>() {

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
        val btnApply: AppCompatButton = view.findViewById(R.id.btn_apply_notification)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.appName
        holder.category.text = app.category
        holder.icon.setImageDrawable(app.icon)
        
        val simulatedMillis = app.usageMillis + (app.simulatedAdjustment * 60000L)
        val simulatedMinutes = (simulatedMillis / 60000).toInt()
        
        val h = simulatedMillis / 3600000
        val m = (simulatedMillis % 3600000) / 60000
        holder.usageTime.text = String.format(Locale.getDefault(), "%dh %02dm", h, m)

        if (app.currentLimitMinutes == 0 && app.recommendedLimit == 0) {
            val realUsageMinutes = (app.usageMillis / 60000).toInt()
            app.recommendedLimit = (realUsageMinutes * 0.85).toInt()
            app.currentLimitMinutes = app.recommendedLimit
        }

        holder.editLimit.setText(app.currentLimitMinutes.toString())
        
        if (app.currentLimitMinutes > 0) {
            val progress = (simulatedMinutes.toDouble() / app.currentLimitMinutes.toDouble() * 100).toInt()
            holder.progressBar.progress = progress.coerceIn(0, 100)
        } else {
            holder.progressBar.progress = 0
        }

        holder.simulatedValue.text = if (app.simulatedAdjustment >= 0) "+${app.simulatedAdjustment} min" else "${app.simulatedAdjustment} min"

        updateApplyButton(holder.btnApply, app.isNotificationEnabled)

        holder.btnPlus.setOnClickListener {
            app.simulatedAdjustment += 5
            notifyItemChanged(position)
            onLimitChanged(app)
        }

        holder.btnMinus.setOnClickListener {
            app.simulatedAdjustment -= 5
            notifyItemChanged(position)
            onLimitChanged(app)
        }
        
        holder.btnApply.setOnClickListener {
            app.isNotificationEnabled = !app.isNotificationEnabled
            updateApplyButton(holder.btnApply, app.isNotificationEnabled)
            onLimitChanged(app)
        }

        holder.editLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newVal = holder.editLimit.text.toString().toIntOrNull() ?: 0
                if (newVal != app.currentLimitMinutes) {
                    app.currentLimitMinutes = newVal
                    notifyItemChanged(position)
                    onLimitChanged(app)
                }
            }
        }
    }

    private fun updateApplyButton(button: AppCompatButton, isEnabled: Boolean) {
        if (isEnabled) {
            button.text = "ATIVADO"
            button.setBackgroundResource(R.drawable.nav_active_bg) // Supondo que este seja o estilo roxo/ativo
            button.setTextColor(ContextCompat.getColor(button.context, R.color.text_white))
        } else {
            button.text = "APLICAR"
            button.setBackgroundResource(R.drawable.inner_card_bg) // Estilo escuro/desativado
            button.setTextColor(ContextCompat.getColor(button.context, R.color.text_grey))
        }
    }

    override fun getItemCount() = apps.size

    fun updateData(newApps: List<AppLimitInfo>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = apps.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = apps[oldPos].packageName == newApps[newPos].packageName
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = 
                apps[oldPos].usageMillis == newApps[newPos].usageMillis &&
                apps[oldPos].simulatedAdjustment == newApps[newPos].simulatedAdjustment &&
                apps[oldPos].currentLimitMinutes == newApps[newPos].currentLimitMinutes &&
                apps[oldPos].isNotificationEnabled == newApps[newPos].isNotificationEnabled
        })
        this.apps = newApps
        diffResult.dispatchUpdatesTo(this)
    }
}
