package com.example.regulador_uso_digital

data class AlertLimit(
    val appName: String,
    val packageName: String,
    val usageMinutes: Int,
    val limitMinutes: Int,
    val timestamp: Long
)
