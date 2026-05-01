package com.example.kairos.wearos.shared

import kotlin.math.sqrt

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

    fun std(): Double = if (count < 2) fallbackStd
    else sqrt(m2 / (count - 1))

    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std()
}