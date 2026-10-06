package com.plantdex.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun dateTime(epochMillis: Long): String =
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

    fun date(epochMillis: Long): String =
        SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(epochMillis))

    fun percent(score: Double): String = "${(score * 100).toInt()}%"

    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
