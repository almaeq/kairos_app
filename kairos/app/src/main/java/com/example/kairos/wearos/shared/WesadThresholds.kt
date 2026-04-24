package com.example.kairos.wearos.shared

/**
 * Copia de los umbrales WESAD para el módulo Wear OS.
 * Fuente de verdad: mobile/detection/WesadThresholds.kt
 *
 * Se duplica para que la app del smartwatch funcione
 * de forma standalone (sin depender del módulo mobile).
 *
 * ⚠ Si se ajustan los umbrales tras la fase Beta, actualizar AMBOS archivos.
 */
object WesadThresholds {
    const val HR_THRESHOLD_BPM         = 85.2
    const val HR_ELEVATION_PERCENT     = 25.0
    const val HRV_RMSSD_THRESHOLD_MS   = 43.4
    const val ACC_MOVEMENT_THRESHOLD   = 0.12
    const val ANALYSIS_WINDOW_SECONDS  = 60L
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2
}