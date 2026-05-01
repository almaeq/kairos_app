package com.example.kairos.mobile.detection

import androidx.health.connect.client.records.HeartRateRecord
import kotlin.math.sqrt

/**
 * Detector de crisis con método Z-Score individual.
 *
 * Cambio respecto a v1:
 *   En lugar de comparar HR y RMSSD contra umbrales fijos WESAD,
 *   calcula el Z-Score respecto al baseline PERSONAL del usuario.
 *   Detección = Z-Score > 2.5σ en HR o RMSSD, estando en reposo.
 *
 *   Esto resuelve el problema de variabilidad inter-sujeto:
 *   una HR de 85 bpm es normal para alguien activo pero alta para
 *   alguien sedentario. El Z-Score normaliza por persona.
 */
class CrisisDetector {

    private var consecutivePositiveWindows = 0

    private val hrBaseline = RunningStats(
        fallbackMean = WesadThresholds.HR_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HR_BASELINE_STD
    )
    private val hrvBaseline = RunningStats(
        fallbackMean = WesadThresholds.HRV_RMSSD_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HRV_RMSSD_BASELINE_STD
    )
    private var calibrationWindows = 0

    fun analyze(
        hrSamples: List<HeartRateRecord.Sample>,
        stepsInWindow: Long,
        accelerometerMagnitude: Double = 0.0
    ): DetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(hrSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(hrSamples)  ?: return null

        val stepsPerMinute       = stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)
        val activeBySteps        = stepsPerMinute > 30
        val activeByAcc          = accelerometerMagnitude > WesadThresholds.ACC_MOVEMENT_THRESHOLD
        val movementFilterPassed = !activeBySteps && !activeByAcc

        if (movementFilterPassed &&
            calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
        }

        val zHr    = hrBaseline.zScore(meanHr)
        val zRmssd = hrvBaseline.zScore(rmssd)

        val hrThresholdExceeded  = zHr    >  WesadThresholds.SENSITIVITY_SIGMAS
        val hrvThresholdExceeded = zRmssd < -WesadThresholds.SENSITIVITY_SIGMAS

        val windowPositive = (hrThresholdExceeded || hrvThresholdExceeded) &&
                movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val isCrisis = consecutivePositiveWindows >=
                WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        return DetectionResult(
            isCrisisDetected     = isCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            movementMagnitude    = accelerometerMagnitude,
            hrThresholdExceeded  = hrThresholdExceeded,
            hrvThresholdExceeded = hrvThresholdExceeded,
            movementFilterPassed = movementFilterPassed
        )
    }

    fun resetConsecutiveCount() { consecutivePositiveWindows = 0 }
    fun getCalibrationStatus(): String =
        "$calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas calibradas"
}
/**
 * Estadísticas incrementales (media y std) para Z-Score en tiempo real.
 * Usa el algoritmo de Welford — no necesita guardar todas las muestras.
 * Fallback a valores WESAD si no hay suficientes datos propios.
 */
class RunningStats(
    private val fallbackMean: Double = 0.0,
    private val fallbackStd: Double  = 1.0
) {
    private var count = 0
    private var mean  = 0.0
    private var m2    = 0.0

    fun add(value: Double) {
        count++
        val delta  = value - mean
        mean      += delta / count
        val delta2 = value - mean
        m2        += delta * delta2
    }

    fun std(): Double = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std()
}