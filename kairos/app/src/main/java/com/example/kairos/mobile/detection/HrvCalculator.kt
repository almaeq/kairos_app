package com.example.kairos.mobile.detection

import androidx.health.connect.client.records.HeartRateRecord
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calcula métricas HRV a partir de muestras de HeartRateRecord.
 *
 * Cubre US#3895 — detección de variaciones en métricas biomédicas.
 *
 * Limitación documentada:
 *   HeartRateRecord expone BPM promedio, no intervalos RR exactos.
 *   Se aproximan los RR como: RR(ms) = 60_000 / BPM.
 *   Para intervalos RR reales desde el smartwatch usar PassiveMonitoringClient
 *   (Health Services API, Wear OS) → ver wearos/sensors/HeartRateDataSource.kt
 */
object HrvCalculator {

    /**
     * Calcula RMSSD en ms a partir de los samples de HeartRateRecord.
     * @return RMSSD en ms, o null si hay menos de 2 muestras.
     */
    fun calculateRmssd(samples: List<HeartRateRecord.Sample>): Double? {
        if (samples.size < 2) return null

        val rrIntervals = samples.map { 60_000.0 / it.beatsPerMinute.toDouble() }
        val successiveDiffs = rrIntervals.zipWithNext { a, b -> b - a }
        if (successiveDiffs.isEmpty()) return null

        val meanSquaredDiff = successiveDiffs.map { it.pow(2) }.average()
        return sqrt(meanSquaredDiff)
    }

    /**
     * Calcula HR promedio en bpm.
     * @return HR promedio, o null si no hay muestras.
     */
    fun calculateMeanHr(samples: List<HeartRateRecord.Sample>): Double? {
        if (samples.isEmpty()) return null
        return samples.map { it.beatsPerMinute.toDouble() }.average()
    }
}