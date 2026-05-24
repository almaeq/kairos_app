package com.example.kairos.detection

import kotlin.math.sqrt

data class WatchDetectionResult(
    val isCrisisDetected:    Boolean,
    val isPreAlert:          Boolean,
    val averageHrBpm:        Double,
    val rmssdMs:             Double,
    val hrThresholdExceeded: Boolean,
    val hrvThresholdExceeded: Boolean,
    val movementFilterPassed: Boolean,
    val calibrationWindows:  Int
)

class RunningStats(
    private val fallbackMean: Double = 0.0,
    private val fallbackStd:  Double = 1.0
) {
    var count = 0; private set
    var mean  = 0.0; private set
    var m2    = 0.0; private set

    fun add(value: Double) {
        count++
        val delta = value - mean
        mean     += delta / count
        m2       += delta * (value - mean)
    }

    fun restore(savedCount: Int, savedMean: Double, savedM2: Double) {
        count = savedCount; mean = savedMean; m2 = savedM2
    }

    fun std(): Double = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std()
}