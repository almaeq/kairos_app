package com.example.kairos.detection

import kotlin.math.sqrt

/**
 * Resultado del análisis de una ventana de señales fisiológicas por [WatchCrisisDetector].
 *
 * Equivalente Wear OS de `DetectionResult` del teléfono. Se construye en cada ciclo
 * de análisis y se usa para determinar qué mensaje enviar al teléfono via
 * Wearable Message API (`/kairos/crisis`, `/kairos/prealerta`, `/kairos/heartbeat`).
 *
 * @property isCrisisDetected `true` si se confirmó una crisis — el modelo superó
 *           [CrisisPredictor.THRESHOLD_CRISIS] durante [WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM]
 *           ventanas consecutivas.
 * @property isPreAlert `true` si la ventana superó [CrisisPredictor.THRESHOLD_PRE_ALERT]
 *           pero todavía no se alcanzó el número de ventanas consecutivas para confirmar.
 * @property averageHrBpm Frecuencia cardíaca media en BPM de la ventana actual.
 * @property rmssdMs Variabilidad cardíaca (RMSSD) en milisegundos de la ventana actual.
 * @property hrThresholdExceeded `true` si el Z-score de HR superó 1.0 respecto al baseline.
 *           Valor informativo — no determina por sí solo la detección de crisis.
 * @property hrvThresholdExceeded `true` si el Z-score de RMSSD superó 1.0 respecto al baseline.
 *           Valor informativo — no determina por sí solo la detección de crisis.
 * @property movementFilterPassed `true` si el movimiento del usuario estaba dentro del umbral
 *           de reposo. `true` significa que el filtro **no invalida** la detección,
 *           NO que se detectó movimiento — la semántica es invertida intencionalmente.
 * @property calibrationWindows Número de ventanas de baseline completadas hasta el momento.
 *           Se incluye para que el teléfono pueda actualizar su indicador de calibración.
 */
data class WatchDetectionResult(
    val isCrisisDetected:     Boolean,
    val isPreAlert:           Boolean,
    val averageHrBpm:         Double,
    val rmssdMs:              Double,
    val hrThresholdExceeded:  Boolean,
    val hrvThresholdExceeded: Boolean,
    val movementFilterPassed: Boolean,
    val calibrationWindows:   Int
)

/**
 * Implementación del algoritmo de Welford para cálculo incremental de media y varianza.
 *
 * Clase compartida entre el módulo del reloj (Wear OS) y el módulo del teléfono (Android).
 * Permite actualizar media y desviación estándar muestra a muestra sin almacenar el
 * historial completo de valores — esencial en el smartwatch donde la memoria es limitada.
 *
 * Mientras `count < 2`, usa los valores de fallback para evitar división por cero
 * en el cálculo de Z-scores durante las primeras ventanas de calibración.
 *
 * @property fallbackMean Media a usar cuando no hay suficientes muestras personales.
 *           Corresponde a la media global del dataset WESAD para esa feature.
 * @property fallbackStd Desviación estándar a usar cuando no hay suficientes muestras.
 *           Corresponde a la std global del dataset WESAD para esa feature.
 */
class RunningStats(
    private val fallbackMean: Double = 0.0,
    private val fallbackStd:  Double = 1.0
) {
    /** Cantidad de muestras acumuladas hasta el momento. */
    var count = 0; private set

    /** Media acumulada calculada de forma incremental. */
    var mean  = 0.0; private set

    /**
     * Suma de diferencias al cuadrado respecto a la media (M2 de Welford).
     * La varianza muestral se obtiene como `m2 / (count - 1)`.
     */
    var m2    = 0.0; private set

    /**
     * Agrega una nueva muestra y actualiza media y M2 de forma incremental.
     *
     * Implementa el algoritmo de Welford en una sola pasada:
     * - `delta  = value - mean_anterior`
     * - `mean  += delta / count`
     * - `m2    += delta * (value - mean_nueva)`
     *
     * @param value Nueva muestra a incorporar al cálculo.
     */
    fun add(value: Double) {
        count++
        val delta = value - mean
        mean     += delta / count
        m2       += delta * (value - mean)
    }

    /**
     * Restaura el estado interno desde valores previamente guardados en [WatchBaseline].
     *
     * Permite retomar el cálculo incremental desde donde se dejó en una sesión anterior,
     * sin necesidad de recalibrar desde cero tras un reinicio del servicio.
     *
     * @param savedCount Cantidad de muestras acumuladas en la sesión anterior.
     * @param savedMean Media acumulada guardada.
     * @param savedM2 M2 acumulada guardada.
     */
    fun restore(savedCount: Int, savedMean: Double, savedM2: Double) {
        count = savedCount; mean = savedMean; m2 = savedM2
    }

    /**
     * Desviación estándar muestral calculada a partir de M2.
     * Retorna [fallbackStd] si hay menos de 2 muestras.
     */
    fun std(): Double = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

    /**
     * Calcula el Z-score de un valor respecto a la distribución acumulada.
     *
     * Usa los parámetros de fallback (umbrales globales WESAD) si hay menos
     * de 2 muestras personales, para evitar Z-scores no representativos.
     *
     * @param value Valor a normalizar.
     * @return Z-score: cuántas desviaciones estándar se aleja [value] de la media.
     */
    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std()
}