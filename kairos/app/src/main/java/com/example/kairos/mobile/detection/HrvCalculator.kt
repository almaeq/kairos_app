package com.example.kairos.mobile.detection

import androidx.health.connect.client.records.HeartRateRecord
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calcula métricas de frecuencia cardíaca y variabilidad cardíaca (HRV)
 * a partir de muestras de [HeartRateRecord] de Health Connect.
 *
 * Cubre US#3895 — detección de variaciones en métricas biomédicas.
 *
 * **Limitación documentada — aproximación de intervalos RR:**
 * [HeartRateRecord] expone únicamente BPM promedio por muestra,
 * no los intervalos R-R exactos entre latidos consecutivos.
 * Para calcular RMSSD se aproximan los intervalos RR como:
 * ```
 * RR(ms) = 60.000 / BPM
 * ```
 * Esta aproximación introduce error cuando la HR varía dentro de una muestra,
 * pero es suficiente para la detección de estrés a nivel de ventanas de 60 segundos.
 *
 * Para intervalos RR reales desde el smartwatch (mayor precisión) usar
 * `PassiveMonitoringClient` de Health Services API (Wear OS)
 * → ver `wearos/sensors/HeartRateDataSource.kt`
 */
object HrvCalculator {

    /**
     * Calcula el RMSSD (Root Mean Square of Successive Differences) en milisegundos.
     *
     * El RMSSD es la métrica estándar de HRV para detección de estrés agudo:
     * valores bajos indican mayor activación del sistema nervioso simpático (estrés),
     * valores altos indican predominio parasimpático (reposo).
     *
     * **Algoritmo:**
     * 1. Convierte cada muestra de BPM a intervalo RR: `RR = 60.000 / BPM`
     * 2. Calcula las diferencias entre intervalos RR consecutivos
     * 3. Eleva cada diferencia al cuadrado
     * 4. Calcula la media de los cuadrados
     * 5. Aplica la raíz cuadrada al resultado
     *
     * @param samples Lista de muestras de HR de la ventana a analizar.
     * @return RMSSD en milisegundos, o `null` si hay menos de 2 muestras
     *         (se necesitan al menos 2 para calcular una diferencia sucesiva).
     */
    fun calculateRmssd(samples: List<HeartRateRecord.Sample>): Double? {
        if (samples.size < 2) return null

        // Convertimos BPM a intervalos RR en milisegundos
        val rrIntervals = samples.map { 60_000.0 / it.beatsPerMinute.toDouble() }

        // Diferencias entre intervalos RR consecutivos (sucesivas)
        val successiveDiffs = rrIntervals.zipWithNext { a, b -> b - a }
        if (successiveDiffs.isEmpty()) return null

        // Media de los cuadrados de las diferencias → raíz cuadrada
        val meanSquaredDiff = successiveDiffs.map { it.pow(2) }.average()
        return sqrt(meanSquaredDiff)
    }

    /**
     * Calcula la frecuencia cardíaca media en BPM sobre una lista de muestras.
     *
     * Se usa como feature principal de entrada al modelo Random Forest,
     * junto con [calculateRmssd], para la detección de crisis de ansiedad.
     *
     * @param samples Lista de muestras de HR de la ventana a analizar.
     * @return HR media en BPM, o `null` si la lista está vacía.
     */
    fun calculateMeanHr(samples: List<HeartRateRecord.Sample>): Double? {
        if (samples.isEmpty()) return null
        return samples.map { it.beatsPerMinute.toDouble() }.average()
    }
}