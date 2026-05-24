package com.example.kairos.detection

/**
 * Parámetros de detección KAIROS — Basados en Random Forest Optimizado.
 *
 * Fuente: kairos_logic.json (Extraído de entrenamiento WESAD)
 * Validación Final: Recall 0.69, Precision 0.88, Accuracy 0.82
 */
object WesadThresholds {

    // ── Referencias estadísticas WESAD (Fallback inicial antes de calibrar) ───
    const val HR_BASELINE_MEAN        = 72.4
    const val HR_BASELINE_STD         = 8.0
    const val HRV_RMSSD_BASELINE_MEAN = 51.0
    const val HRV_RMSSD_BASELINE_STD  = 12.0

    // ── Configuración del sistema ─────────────────────────────────────────────
    const val ACC_MOVEMENT_THRESHOLD         = 0.12
    const val ANALYSIS_WINDOW_SECONDS        = 60L
    const val MIN_CALIBRATION_WINDOWS        = 3
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2
}