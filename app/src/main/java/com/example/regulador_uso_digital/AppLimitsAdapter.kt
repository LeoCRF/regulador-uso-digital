package com.example.regulador_uso_digital

import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
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

class AppLimitsAdapter(
    private var apps: List<AppLimitInfo>,
    private val onLimitChanged: (AppLimitInfo) -> Unit
) : RecyclerView.Adapter<AppLimitsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val category: TextView = view.findViewById(R.id.app_category)
        val usageTime: TextView = view.findViewById(R.id.usage_time)
        val dailyInsight: TextView = view.findViewById(R.id.tv_daily_insight)
        val todayUsage: TextView = view.findViewById(R.id.tv_today_usage)
        val progressBar: ProgressBar = view.findViewById(R.id.usage_progress)
        val editLimit: EditText = view.findViewById(R.id.edit_limit)
        val limitFormatted: TextView = view.findViewById(R.id.tv_limit_formatted)
        val btnMinus: ImageButton = view.findViewById(R.id.btn_minus)
        val btnPlus: ImageButton = view.findViewById(R.id.btn_plus)
        val simulatedValue: TextView = view.findViewById(R.id.simulated_value)
        val btnApply: AppCompatButton = view.findViewById(R.id.btn_apply_notification)
        
        var currentTextWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        
        holder.name.text = app.appName
        holder.category.text = app.category
        holder.icon.setImageDrawable(app.icon)
        holder.usageTime.text = app.weeklyUsageFormatted
        
        val avgStr = formatMinutes(app.dailyAverageMinutes)
        val recStr = formatMinutes(app.recommendedLimit)
        holder.dailyInsight.text = context.getString(R.string.insight_format, app.weeklyUsageFormatted, avgStr, recStr)

        val currentUsageMins = (app.dailyUsageMillis / 60000).toInt() + app.simulatedAdjustment
        holder.todayUsage.text = context.getString(R.string.progress_format, formatMinutes(currentUsageMins), formatMinutes(app.currentLimitMinutes))

        holder.currentTextWatcher?.let { holder.editLimit.removeTextChangedListener(it) }
        holder.editLimit.setText(app.currentLimitMinutes.toString())
        holder.limitFormatted.text = context.getString(R.string.equivalent_format, formatMinutes(app.currentLimitMinutes))
        
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val mins = s.toString().toIntOrNull() ?: 0
                holder.limitFormatted.text = context.getString(R.string.equivalent_format, formatMinutes(mins))
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        holder.editLimit.addTextChangedListener(textWatcher)
        holder.currentTextWatcher = textWatcher

        if (app.currentLimitMinutes > 0) {
            val progress = (currentUsageMins.toDouble() / app.currentLimitMinutes.toDouble() * 100).toInt()
            holder.progressBar.progress = progress.coerceIn(0, 100)
        } else {
            holder.progressBar.progress = 0
        }

        holder.simulatedValue.text = if (app.simulatedAdjustment >= 0) "+${app.simulatedAdjustment} min" else "${app.simulatedAdjustment} min"

        holder.btnPlus.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                apps[pos].simulatedAdjustment += 5
                notifyItemChanged(pos)
                onLimitChanged(apps[pos])
            }
        }

        holder.btnMinus.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                apps[pos].simulatedAdjustment -= 5
                notifyItemChanged(pos)
                onLimitChanged(apps[pos])
            }
        }

        updateApplyButton(holder.btnApply, app.isNotificationEnabled)
        holder.btnApply.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val currentApp = apps[pos]
                currentApp.isNotificationEnabled = !currentApp.isNotificationEnabled
                updateApplyButton(holder.btnApply, currentApp.isNotificationEnabled)
                onLimitChanged(currentApp)
            }
        }

        holder.editLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val newVal = holder.editLimit.text.toString().toIntOrNull() ?: 0
                    if (newVal != apps[pos].currentLimitMinutes) {
                        apps[pos].currentLimitMinutes = newVal
                        onLimitChanged(apps[pos])
                    }
                }
            }
        }
    }

    private fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0 min"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m} min"
        }
    }

    private fun updateApplyButton(button: AppCompatButton, isEnabled: Boolean) {
        val context = button.context
        if (isEnabled) {
            button.text = context.getString(R.string.btn_ativado)
            button.setBackgroundResource(R.drawable.nav_active_bg)
            button.setTextColor(ContextCompat.getColor(context, R.color.text_white))
        } else {
            button.text = context.getString(R.string.btn_aplicar)
            button.setBackgroundResource(R.drawable.inner_card_bg)
            button.setTextColor(ContextCompat.getColor(context, R.color.text_grey))
        }
    }

    override fun getItemCount() = apps.size

    fun updateData(newApps: List<AppLimitInfo>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = apps.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = 
                apps[oldPos].packageName == newApps[newPos].packageName
            
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val o = apps[oldPos]
                val n = newApps[newPos]
                // Comparamos campos lógicos, ignorando a instância física do ícone
                return o.packageName == n.packageName &&
                        o.weeklyUsageMillis == n.weeklyUsageMillis &&
                        o.dailyUsageMillis == n.dailyUsageMillis &&
                        o.currentLimitMinutes == n.currentLimitMinutes &&
                        o.simulatedAdjustment == n.simulatedAdjustment &&
                        o.isNotificationEnabled == n.isNotificationEnabled
            }
        })
        this.apps = newApps
        diffResult.dispatchUpdatesTo(this)
    }
}
