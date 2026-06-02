package com.example.regulador_uso_digital

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertsAdapter(private val alerts: List<AlertLimit>) : RecyclerView.Adapter<AlertsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.alert_title)
        val message: TextView = view.findViewById(R.id.alert_message)
        val time: TextView = view.findViewById(R.id.alert_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alerta, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]
        holder.title.text = "Limite atingido: ${alert.appName}"
        holder.message.text = "Uso: ${alert.usageMinutes} min | Limite: ${alert.limitMinutes} min"
        
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = sdf.format(Date(alert.timestamp))
    }

    override fun getItemCount() = alerts.size
}
