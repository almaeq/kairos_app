package com.example.kairos.mobile.detection

/**
 * Umbrales derivados del dataset WESAD (Schmidt et al., 2018).
 * n=15 sujetos, ventana=60s.
 *
 * Fuente: umbrales_kairos.json
 * Cubre US#3556 — distinguir crisis de actividad física normal.
 *
 * ⚠ Estos valores son el punto de partida calibrado con WESAD.
 *   Se refinarán con datos reales en la fase Beta (Sprint 4).
 */
object WesadThresholds {

    // ── HR (Frecuencia Cardíaca) ──────────────────────────────────────────────
    /** Punto de corte calculado (baseline 72.4, stress 97.9 → corte 85.2) */
    const val HR_THRESHOLD_BPM      = 85.2
    const val HR_BASELINE_MEAN      = 72.4   // referencia estadística WESAD
    const val HR_STRESS_MEAN        = 97.9   // referencia estadística WESAD

    /** Elevación relativa sobre baseline personal (derivado del ratio WESAD ≈35%; conservador 25%) */
    const val HR_ELEVATION_PERCENT  = 25.0

    // ── HRV - RMSSD ───────────────────────────────────────────────────────────
    /** Punto de corte calculado (baseline 51.0 ms, stress 35.8 ms → corte 43.4 ms) */
    const val HRV_RMSSD_THRESHOLD_MS   = 43.4
    const val HRV_RMSSD_BASELINE_MEAN  = 51.0  // referencia estadística WESAD
    const val HRV_RMSSD_STRESS_MEAN    = 35.8  // referencia estadística WESAD

    // ── Acelerómetro ──────────────────────────────────────────────────────────
    /** Magnitud vectorial ACC en g. Por encima → actividad física, NO crisis. */
    const val ACC_MOVEMENT_THRESHOLD   = 0.12

    // ── Ventana temporal ──────────────────────────────────────────────────────
    /** Alineado con ventana WESAD (60s) para máxima fidelidad con el dataset. */
    const val ANALYSIS_WINDOW_SECONDS  = 60L

    /** Ventanas consecutivas positivas requeridas antes de confirmar crisis. */
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 2
}