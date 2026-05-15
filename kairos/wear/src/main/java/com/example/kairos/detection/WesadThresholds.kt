package com.example.kairos.detection

/**
 * Parámetros de detección KAIROS — Basados en Random Forest Optimizado.
 *
 * Fuente: kairos_logic.json (Extraído de entrenamiento WESAD)
 * Validación Final: Recall 0.69, Precision 0.88, Accuracy 0.82
 */
object WesadThresholds {

    // ── Umbrales del Modelo Random Forest ─────────────────────────────────────
    /** Umbral de activación para el pulso (Z-HR) */
    const val Z_HR_TRIGGER = 1.2

    /** Umbral de activación para HRV (Z-RMSSD). El estrés baja la variabilidad. */
    const val Z_RMSSD_TRIGGER = -1.0

    /** Umbral para el Score Combinado (Promedio balanceado de ambos) */
    const val Z_COMBINED_TRIGGER = 1.15

    // ── Referencias estadísticas WESAD (Fallback) ─────────────────────────────
    const val HR_BASELINE_MEAN      = 72.4
    const val HR_BASELINE_STD       = 8.0
    const val HRV_RMSSD_BASELINE_MEAN = 51.0
    const val HRV_RMSSD_BASELINE_STD  = 12.0

    // ── Filtros y Ventanas ────────────────────────────────────────────────────
    const val ACC_MOVEMENT_THRESHOLD = 0.12
    const val ANALYSIS_WINDOW_SECONDS = 60L
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2
    const val MIN_CALIBRATION_WINDOWS = 3
}