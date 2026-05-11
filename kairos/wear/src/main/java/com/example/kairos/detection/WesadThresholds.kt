package com.example.kairos.detection

object WesadThresholds {
    const val SENSITIVITY_SIGMAS = 2.5
    const val HR_BASELINE_MEAN = 72.4
    const val HR_BASELINE_STD = 8.0
    const val HRV_RMSSD_BASELINE_MEAN = 51.0
    const val HRV_RMSSD_BASELINE_STD = 12.0
    const val ACC_MOVEMENT_THRESHOLD = 0.12
    const val ANALYSIS_WINDOW_SECONDS = 60L
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2
    const val MIN_CALIBRATION_WINDOWS = 3
}