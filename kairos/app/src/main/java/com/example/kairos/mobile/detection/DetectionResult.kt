package com.example.kairos.mobile.detection

import java.time.Instant

/**
 * Resultado del análisis de una ventana de señales fisiológicas por [CrisisDetector].
 *
 * Encapsula todas las métricas calculadas durante el análisis de una ventana de 60 segundos,
 * incluyendo el veredicto de detección, los valores fisiológicos medidos y el estado
 * de cada capa del pipeline de filtrado.
 *
 * Este objeto se propaga desde el reloj al teléfono cuando se detecta una crisis,
 * y se usa para construir el [CrisisEpisode] que se persiste en Room.
 *
 * @property isCrisisDetected `true` si se confirmó una crisis — es decir, si el modelo
 *           superó [CrisisPredictor.THRESHOLD_CRISIS] durante
 *           [WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM] ventanas consecutivas.
 * @property averageHrBpm Frecuencia cardíaca media en BPM calculada sobre la ventana.
 * @property rmssdMs Variabilidad cardíaca (RMSSD) en milisegundos calculada sobre la ventana.
 * @property movementMagnitude Magnitud del vector de aceleración del reloj durante la ventana.
 *           Valor bajo (~0.01g) indica reposo; valor alto indica movimiento activo.
 * @property hrThresholdExceeded `true` si el Z-score de HR superó 1.0 respecto al baseline.
 *           Valor informativo — no determina por sí solo la detección de crisis.
 * @property hrvThresholdExceeded `true` si el Z-score de RMSSD superó 1.0 respecto al baseline.
 *           Valor informativo — no determina por sí solo la detección de crisis.
 * @property movementFilterPassed `true` si el movimiento del usuario estaba dentro del
 *           umbral de reposo ([WesadThresholds.ACC_MOVEMENT_THRESHOLD]).
 *           Importante: `true` significa que el filtro de movimiento **no invalida**
 *           la detección (el usuario está quieto), NO que se detectó movimiento.
 * @property timestamp Momento en que se generó este resultado.
 */
data class DetectionResult(
    val isCrisisDetected:    Boolean,
    val averageHrBpm:        Double,
    val rmssdMs:             Double,
    val movementMagnitude:   Double,
    val hrThresholdExceeded: Boolean,
    val hrvThresholdExceeded: Boolean,
    val movementFilterPassed: Boolean,
    val timestamp: Instant = Instant.now()
) {
    /**
     * Genera una representación compacta del resultado para logging en Logcat.
     *
     * @return String con el formato:
     *         `Crisis=true | HR=82.3bpm | RMSSD=28.4ms | ACC=0.012g`
     */
    fun toLogString(): String =
        "Crisis=$isCrisisDetected | " +
                "HR=${"%.1f".format(averageHrBpm)}bpm | " +
                "RMSSD=${"%.1f".format(rmssdMs)}ms | " +
                "ACC=${"%.3f".format(movementMagnitude)}g"
}