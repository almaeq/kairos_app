package com.example.kairos.mobile.detection

import java.time.Instant

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