package com.example.regulador_uso_digital

import android.graphics.drawable.Drawable

data class AppLimitInfo(
    val packageName: String,
    val appName: String,
    val category: String,
    val weeklyUsageFormatted: String,
    val icon: Drawable,
    val dailyUsageMillis: Long,
    val weeklyUsageMillis: Long,
    val dailyAverageMinutes: Int,
    var currentLimitMinutes: Int = 0,
    var simulatedAdjustment: Int = 0,
    var recommendedLimit: Int = 0,
    var isNotificationEnabled: Boolean = false
)
