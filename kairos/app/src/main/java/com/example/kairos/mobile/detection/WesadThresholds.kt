package com.example.kairos.mobile.detection

/**
 * Parámetros de detección KAIROS — Método Z-Score individual.
 *
 * Fuente: kairos_detection_params.json
 * Validación: WESAD 15 sujetos
 *
 * Cambio respecto a v1:
 *   Se abandona el umbral estático (punto medio entre clases)
 *   por Z-Score individual: cada usuario tiene su propio baseline
 *   y la detección se activa cuando la señal se aleja 2.5σ de ese baseline.
 *
 *   AUC HR:       0.92  ← excelente discriminación
 *   AUC RMSSD:    0.659 ← discriminación moderada
 *   AUC combinado: 0.868
 */
object WesadThresholds {

    // ── Método Z-Score ────────────────────────────────────────────────────────
    /**
     * Umbral en sigmas para activar detección.
     * 2.5σ = 98.7% certeza estadística (regla empírica).
     * Validado contra σ=2.0 (demasiados falsos positivos)
     * y σ=3.0 (demasiados falsos negativos).
     */
    const val SENSITIVITY_SIGMAS = 2.5

    // ── Referencias estadísticas WESAD (15 sujetos) ───────────────────────────
    // Solo se usan como fallback si el usuario no tiene baseline calibrado.
    const val HR_BASELINE_MEAN      = 72.4
    const val HR_BASELINE_STD       = 8.0   // estimado del dataset
    const val HRV_RMSSD_BASELINE_MEAN = 51.0
    const val HRV_RMSSD_BASELINE_STD  = 12.0 // estimado del dataset

    // ── Acelerómetro ──────────────────────────────────────────────────────────
    // Del notebook: baseline=63.4, stress=62.9 → prácticamente iguales.
    // El ACC sigue siendo útil como filtro de ejercicio físico.
    const val ACC_MOVEMENT_THRESHOLD = 0.12

    // ── Ventana temporal ──────────────────────────────────────────────────────
    const val ANALYSIS_WINDOW_SECONDS = 60L
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2

    // ── Calibración mínima ────────────────────────────────────────────────────
    /** Ventanas de baseline necesarias antes de activar Z-Score personal. */
    const val MIN_CALIBRATION_WINDOWS = 3
}