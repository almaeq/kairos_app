package com.example.kairos.db

import android.content.Context
import com.example.kairos.detection.WesadThresholds

object WatchBaseline {

    private const val PREFS = "kairos_baseline"

    fun save(context: Context,
             hrCount: Int, hrMean: Double, hrM2: Double,
             hrvCount: Int, hrvMean: Double, hrvM2: Double,
             calibrationWindows: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("hr_count", hrCount)
            putFloat("hr_mean", hrMean.toFloat())
            putFloat("hr_m2", hrM2.toFloat())
            putInt("hrv_count", hrvCount)
            putFloat("hrv_mean", hrvMean.toFloat())
            putFloat("hrv_m2", hrvM2.toFloat())
            putInt("cal_windows", calibrationWindows)
            apply()
        }
    }

    fun load(context: Context): BaselineData? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val windows = prefs.getInt("cal_windows", 0)
        if (windows < WesadThresholds.MIN_CALIBRATION_WINDOWS) return null
        return BaselineData(
            hrCount = prefs.getInt("hr_count", 0),
            hrMean = prefs.getFloat("hr_mean", 0f).toDouble(),
            hrM2 = prefs.getFloat("hr_m2", 0f).toDouble(),
            hrvCount = prefs.getInt("hrv_count", 0),
            hrvMean = prefs.getFloat("hrv_mean", 0f).toDouble(),
            hrvM2 = prefs.getFloat("hrv_m2", 0f).toDouble(),
            calibrationWindows = windows
        )
    }

    fun isCalibrated(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("cal_windows", 0) >= WesadThresholds.MIN_CALIBRATION_WINDOWS
}

data class BaselineData(
    val hrCount: Int, val hrMean: Double, val hrM2: Double,
    val hrvCount: Int, val hrvMean: Double, val hrvM2: Double,
    val calibrationWindows: Int
)