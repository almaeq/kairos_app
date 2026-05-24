package com.example.kairos.detection

import kotlin.math.pow
import kotlin.math.sqrt

object HrvCalculator {

    fun calculateRmssd(bpmSamples: List<Double>): Double? {
        if (bpmSamples.size < 2) return null
        val rrIntervals = bpmSamples.map { 60_000.0 / it }
        val diffs = rrIntervals.zipWithNext { a, b -> b - a }
        if (diffs.isEmpty()) return null
        return sqrt(diffs.map { it.pow(2) }.average())
    }

    fun calculateMeanHr(bpmSamples: List<Double>): Double? {
        if (bpmSamples.isEmpty()) return null
        return bpmSamples.average()
    }
}