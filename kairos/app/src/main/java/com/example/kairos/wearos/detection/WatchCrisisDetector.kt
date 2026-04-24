package com.example.kairos.wearos.detection

import com.example.kairos.wearos.shared.WesadThresholds
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detector de crisis para la app Wear OS.
 *
 * Cubre US#3556 — en el smartwatch recibe HR e intervalos RR reales
 * (desde PassiveMonitoringClient) y la magnitud ACC directa del sensor,
 * logrando la Capa 3 con máxima precisión sin depender de Health Connect.
 *
 * @param hrBpm                   HR promedio de la ventana (bpm)
 * @param rrIntervalsSamples      Lista de intervalos RR en ms (desde Health Services API)
 * @param accelerometerMagnitude  Magnitud vectorial ACC (g) leída del SensorManager
 */
class WatchCrisisDetector {

    private var consecutivePositiveWindows = 0
    private var userBaselineHr: Double? = null

    data class WatchSample(
        val hrBpm: Double,
        val rrIntervals: List<Double>,   // ms — disponibles en Wear OS
        val accMagnitude: Double         // g
    )

    fun analyze(sample: WatchSample): Boolean {
        val rmssd = calculateRmssd(sample.rrIntervals) ?: return false

        // Capa 1: taquicardia
        val hrAbsolute = sample.hrBpm >= WesadThresholds.HR_THRESHOLD_BPM
        val hrRelative = userBaselineHr?.let {
            ((sample.hrBpm - it) / it) * 100 >= WesadThresholds.HR_ELEVATION_PERCENT
        } ?: false
        val hrFired = hrAbsolute || hrRelative

        // Capa 2: caída HRV
        val hrvFired = rmssd < WesadThresholds.HRV_RMSSD_THRESHOLD_MS

        // Capa 3: reposo (ACC directo del sensor)
        val inRest = sample.accMagnitude <= WesadThresholds.ACC_MOVEMENT_THRESHOLD

        val windowPositive = (hrFired || hrvFired) && inRest

        if (windowPositive) {
            consecutivePositiveWindows++
        } else {
            consecutivePositiveWindows = 0
            if (inRest && sample.hrBpm < WesadThresholds.HR_THRESHOLD_BPM) {
                updateBaseline(sample.hrBpm)
            }
        }

        return consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM
    }

    private fun calculateRmssd(rrIntervals: List<Double>): Double? {
        if (rrIntervals.size < 2) return null
        val diffs = rrIntervals.zipWithNext { a, b -> b - a }
        return sqrt(diffs.map { it.pow(2) }.average())
    }

    private fun updateBaseline(hr: Double) {
        userBaselineHr = userBaselineHr?.let { 0.9 * it + 0.1 * hr } ?: hr
    }

    fun reset() { consecutivePositiveWindows = 0 }
}