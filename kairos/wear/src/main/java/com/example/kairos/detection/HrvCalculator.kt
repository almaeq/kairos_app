package com.example.kairos.detection

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calcula métricas de frecuencia cardíaca y variabilidad cardíaca (HRV)
 * a partir de muestras de BPM del sensor del reloj.
 *
 * Equivalente Wear OS de `HrvCalculator` del teléfono, con una diferencia clave:
 * recibe `List<Double>` de BPM directamente en lugar de `List<HeartRateRecord.Sample>`,
 * porque en el reloj los datos provienen de `PassiveMonitoringClient` (Health Services API)
 * y no de Health Connect.
 *
 * **Limitación documentada — aproximación de intervalos RR:**
 * Al igual que en el teléfono, el sensor del reloj expone BPM promedio por muestra,
 * no intervalos R-R exactos. El RMSSD se aproxima convirtiendo BPM a RR:
 * `RR(ms) = 60.000 / BPM`
 * Esta aproximación es suficiente para detección de estrés a nivel de ventanas de 60s.
 */
object HrvCalculator {

    /**
     * Calcula el RMSSD en milisegundos a partir de muestras de BPM del reloj.
     *
     * Convierte cada muestra de BPM a intervalo RR, calcula las diferencias
     * sucesivas, las eleva al cuadrado, promedia y aplica la raíz cuadrada.
     *
     * @param bpmSamples Lista de muestras de HR en BPM de la ventana a analizar.
     * @return RMSSD en milisegundos, o `null` si hay menos de 2 muestras.
     */
    fun calculateRmssd(bpmSamples: List<Double>): Double? {
        if (bpmSamples.size < 2) return null

        // Convertimos BPM a intervalos RR en milisegundos
        val rrIntervals = bpmSamples.map { 60_000.0 / it }

        // Diferencias entre intervalos RR consecutivos
        val diffs = rrIntervals.zipWithNext { a, b -> b - a }
        if (diffs.isEmpty()) return null

        return sqrt(diffs.map { it.pow(2) }.average())
    }

    /**
     * Calcula la frecuencia cardíaca media en BPM sobre una lista de muestras.
     *
     * @param bpmSamples Lista de muestras de HR en BPM de la ventana a analizar.
     * @return HR media en BPM, o `null` si la lista está vacía.
     */
    fun calculateMeanHr(bpmSamples: List<Double>): Double? {
        if (bpmSamples.isEmpty()) return null
        return bpmSamples.average()
    }
}