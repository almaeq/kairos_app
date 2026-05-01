package com.example.kairos.wearos.shared

/**
 * Parámetros de detección KAIROS para módulo Wear OS.
 * Fuente de verdad: mobile/detection/WesadThresholds.kt
 *
 * Método: Z-Score individual con σ=2.5
 * Validación: WESAD 15 sujetos
 * AUC combinado: 0.868
 *
 * ⚠ Si se ajustan los parámetros tras la fase Beta, actualizar AMBOS archivos.
 */
object WesadThresholds {

    // ── Método Z-Score ────────────────────────────────────────────────────────
    const val SENSITIVITY_SIGMAS = 2.5

    // ── Referencias WESAD (fallback si no hay baseline calibrado) ─────────────
    const val HR_BASELINE_MEAN        = 72.4
    const val HR_BASELINE_STD         = 8.0
    const val HRV_RMSSD_BASELINE_MEAN = 51.0
    const val HRV_RMSSD_BASELINE_STD  = 12.0

    // ── Acelerómetro ──────────────────────────────────────────────────────────
    const val ACC_MOVEMENT_THRESHOLD  = 0.12

    // ── Ventana temporal ──────────────────────────────────────────────────────
    const val ANALYSIS_WINDOW_SECONDS = 60L
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2

    // ── Calibración ───────────────────────────────────────────────────────────
    const val MIN_CALIBRATION_WINDOWS = 3
}