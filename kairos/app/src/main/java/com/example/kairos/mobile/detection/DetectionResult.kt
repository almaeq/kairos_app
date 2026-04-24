package com.example.kairos.mobile.detection

import java.time.Instant

/**
 * Resultado de un ciclo de análisis biomédico.
 *
 * Cubre US#3895 (variaciones de métricas biomédicas)
 * y US#3556 (distinguir crisis de actividad física).
 *
 * @param isCrisisDetected   true si se confirman N ventanas consecutivas positivas
 * @param averageHrBpm       HR promedio en la ventana (bpm)
 * @param rmssdMs            HRV-RMSSD calculado en la ventana (ms)
 * @param movementMagnitude  magnitud ACC del smartwatch (g); 0.0 si no disponible
 * @param hrThresholdExceeded  Capa 1: HR supera umbral WESAD
 * @param hrvThresholdExceeded Capa 2: RMSSD cae por debajo del umbral WESAD
 * @param movementFilterPassed Capa 3: usuario en reposo → crisis posible (true = reposo)
 */
data class DetectionResult(
    val isCrisisDetected: Boolean,
    val averageHrBpm: Double,
    val rmssdMs: Double,
    val movementMagnitude: Double,
    val hrThresholdExceeded: Boolean,
    val hrvThresholdExceeded: Boolean,
    val movementFilterPassed: Boolean,
    val timestamp: Instant = Instant.now()
) {
    fun toLogString(): String =
        "Crisis=$isCrisisDetected | " +
                "HR=${"%.1f".format(averageHrBpm)}bpm | " +
                "RMSSD=${"%.1f".format(rmssdMs)}ms | " +
                "ACC=${"%.3f".format(movementMagnitude)}g"
}