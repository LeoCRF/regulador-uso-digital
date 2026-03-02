package com.example.regulador_uso_digital

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppUsageInfo(
    val appName: String, 
    val formattedTime: String, 
    val appIcon: Drawable,
    val timeMillis: Long
)

class UsageStatsAdapter(
    private var usageStats: List<AppUsageInfo>,
    private var totalDailyTime: Long = 0
) : RecyclerView.Adapter<UsageStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameTextView: TextView = view.findViewById(R.id.app_name_text_view)
        val usageTimeTextView: TextView = view.findViewById(R.id.usage_time_text_view)
        val appIconImageView: ImageView = view.findViewById(R.id.app_icon_image_view)
        val progressBar: ProgressBar = view.findViewById(R.id.app_usage_progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = usageStats[position]
        holder.appNameTextView.text = stat.appName
        holder.usageTimeTextView.text = stat.formattedTime
        holder.appIconImageView.setImageDrawable(stat.appIcon)
        
        // Calcular progresso: (tempo do app / tempo total do dia) * 100
        if (totalDailyTime > 0) {
            val progress = ((stat.timeMillis.toDouble() / totalDailyTime.toDouble()) * 100).toInt()
            holder.progressBar.progress = progress
        } else {
            holder.progressBar.progress = 0
        }
    }

    override fun getItemCount() = usageStats.size

    fun updateData(newUsageStats: List<AppUsageInfo>, newTotalDailyTime: Long) {
        this.usageStats = newUsageStats
        this.totalDailyTime = newTotalDailyTime
        notifyDataSetChanged()
    }
}