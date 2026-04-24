package com.example.kairos.mobile.detection

import androidx.health.connect.client.records.HeartRateRecord


/**
 * Detector de crisis multivariable con umbrales WESAD.
 *
 * Cubre US#3556 — detectar caídas en VFC + taquicardia en reposo
 * para distinguir una crisis de actividad física normal.
 *
 * Lógica de 3 capas (según anteproyecto KAIROS):
 *   Capa 1 — HR:  ¿taquicardia? (umbral fijo WESAD + umbral relativo personal)
 *   Capa 2 — HRV: ¿caída de RMSSD? (umbral fijo WESAD)
 *   Capa 3 — ACC: ¿usuario en reposo? (filtro de falsos positivos)
 *
 *   Crisis = (Capa1 OR Capa2) AND Capa3
 *   Confirmado = N ventanas consecutivas positivas
 */
class CrisisDetector {

    private var consecutivePositiveWindows = 0

    /**
     * Baseline HR personal. Se calibra dinámicamente con EMA (α=0.1)
     * cuando el usuario está claramente en calma.
     * Inicialmente null → sólo se usa el umbral absoluto WESAD.
     */
    private var userBaselineHr: Double? = null

    /**
     * Analiza una ventana de datos y retorna el resultado de detección.
     *
     * @param hrSamples           Muestras HR de la ventana actual
     * @param stepsInWindow       Pasos en la ventana (proxy movimiento desde Health Connect)
     * @param accelerometerMagnitude  Magnitud ACC del smartwatch (0.0 si no disponible)
     * @return DetectionResult, o null si no hay suficientes datos
     */
    fun analyze(
        hrSamples: List<HeartRateRecord.Sample>,
        stepsInWindow: Long,
        accelerometerMagnitude: Double = 0.0
    ): DetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(hrSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(hrSamples)  ?: return null

        // ── Capa 1: taquicardia ───────────────────────────────────────────────
        val hrAbsolute = meanHr >= WesadThresholds.HR_THRESHOLD_BPM
        val hrRelative = userBaselineHr?.let { baseline ->
            ((meanHr - baseline) / baseline) * 100 >= WesadThresholds.HR_ELEVATION_PERCENT
        } ?: false
        val hrThresholdExceeded = hrAbsolute || hrRelative

        // ── Capa 2: caída HRV ─────────────────────────────────────────────────
        val hrvThresholdExceeded = rmssd < WesadThresholds.HRV_RMSSD_THRESHOLD_MS

        // ── Capa 3: filtro de movimiento (falsos positivos) ───────────────────
        val stepsPerMinute      = stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)
        val activeBySteps       = stepsPerMinute > 30
        val activeByAcc         = accelerometerMagnitude > WesadThresholds.ACC_MOVEMENT_THRESHOLD
        // true = usuario en reposo → crisis posible
        val movementFilterPassed = !activeBySteps && !activeByAcc

        // ── Decisión de ventana ───────────────────────────────────────────────
        val windowPositive = (hrThresholdExceeded || hrvThresholdExceeded) && movementFilterPassed

        if (windowPositive) {
            consecutivePositiveWindows++
        } else {
            consecutivePositiveWindows = 0
            // Actualizar baseline cuando el usuario está en calma
            if (movementFilterPassed && meanHr < WesadThresholds.HR_THRESHOLD_BPM) {
                updateBaseline(meanHr)
            }
        }

        val isCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

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
    fun getBaselineHr(): Double? = userBaselineHr

    /** EMA con α=0.1 para adaptación gradual al baseline personal. */
    private fun updateBaseline(currentHr: Double) {
        userBaselineHr = userBaselineHr?.let { 0.9 * it + 0.1 * currentHr } ?: currentHr
    }
}