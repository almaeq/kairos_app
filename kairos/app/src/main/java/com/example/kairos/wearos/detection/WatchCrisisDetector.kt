package com.example.kairos.wearos.detection

import com.example.kairos.wearos.shared.WesadThresholds
import com.example.kairos.wearos.shared.RunningStats
import kotlin.math.sqrt

/**
 * Detector de crisis para la app Wear OS — Método Z-Score individual.
 *
 * Ventaja sobre la app móvil: recibe intervalos RR reales desde
 * PassiveMonitoringClient, no aproximados desde BPM.
 * Esto hace el RMSSD más preciso y el Z-Score más confiable.
 *
 * Cubre US#3556 — distinguir crisis de actividad física normal.
 */
class WatchCrisisDetector {

    private var consecutivePositiveWindows = 0
    private val hrBaseline  = RunningStats(
        fallbackMean = WesadThresholds.HR_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HR_BASELINE_STD
    )
    private val hrvBaseline = RunningStats(
        fallbackMean = WesadThresholds.HRV_RMSSD_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HRV_RMSSD_BASELINE_STD
    )
    private var calibrationWindows = 0

    data class WatchSample(
        val hrBpm: Double,
        val rrIntervals: List<Double>,  // ms — reales desde Health Services API
        val accMagnitude: Double        // g — directo del SensorManager
    )

    fun analyze(sample: WatchSample): Boolean {
        val rmssd = calculateRmssd(sample.rrIntervals) ?: return false

        // ── Capa 3: filtro de movimiento ──────────────────────────────────────
        val inRest = sample.accMagnitude <= WesadThresholds.ACC_MOVEMENT_THRESHOLD

        // ── Calibración del baseline personal ────────────────────────────────
        if (inRest && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(sample.hrBpm)
            hrvBaseline.add(rmssd)
            calibrationWindows++
        }

        // ── Z-Scores ──────────────────────────────────────────────────────────
        val zHr    = hrBaseline.zScore(sample.hrBpm)
        val zRmssd = hrvBaseline.zScore(rmssd)

        // HR sube en estrés → Z positivo supera umbral
        val hrFired  = zHr > WesadThresholds.SENSITIVITY_SIGMAS

        // RMSSD baja en estrés → Z negativo supera umbral
        val hrvFired = zRmssd < -WesadThresholds.SENSITIVITY_SIGMAS

        // ── Decisión ──────────────────────────────────────────────────────────
        val windowPositive = (hrFired || hrvFired) && inRest

        if (windowPositive) {
            consecutivePositiveWindows++
        } else {
            consecutivePositiveWindows = 0
        }

        return consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM
    }

    private fun calculateRmssd(rrIntervals: List<Double>): Double? {
        if (rrIntervals.size < 2) return null
        val diffs = rrIntervals.zipWithNext { a, b -> b - a }
        return sqrt(diffs.map { it * it }.average())
    }

    fun reset() { consecutivePositiveWindows = 0 }
    fun getCalibrationStatus(): String =
        "$calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas calibradas"
}

/**
 * Estadísticas incrementales para Z-Score en tiempo real.
 * Algoritmo de Welford — no necesita guardar todas las muestras.
 */
