package com.example.regulador_uso_digital

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppUsageInfo(val appName: String, val formattedTime: String)

class UsageStatsAdapter(private var usageStats: List<AppUsageInfo>) : RecyclerView.Adapter<UsageStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameTextView: TextView = view.findViewById(R.id.app_name_text_view)
        val usageTimeTextView: TextView = view.findViewById(R.id.usage_time_text_view)
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
    }

    override fun getItemCount() = usageStats.size

    fun updateData(newUsageStats: List<AppUsageInfo>) {
        this.usageStats = newUsageStats
        notifyDataSetChanged()
    }
}