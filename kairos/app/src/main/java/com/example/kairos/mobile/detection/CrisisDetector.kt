package com.example.kairos.mobile.detection

import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import com.example.kairos.mobile.data.BaselineRepository
import kotlin.math.sqrt

class CrisisDetector {

    private var consecutivePositiveWindows = 0

    private val hrBaseline = RunningStats(
        fallbackMean = WesadThresholds.HR_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HR_BASELINE_STD
    )
    private val hrvBaseline = RunningStats(
        fallbackMean = WesadThresholds.HRV_RMSSD_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HRV_RMSSD_BASELINE_STD
    )
    private var calibrationWindows = 0

    fun analyze(
        hrSamples: List<HeartRateRecord.Sample>,
        stepsInWindow: Long,
        accelerometerMagnitude: Double = 0.0
    ): DetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(hrSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(hrSamples)  ?: return null

        val stepsPerMinute       = stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)
        val movementFilterPassed = stepsPerMinute <= 30 && accelerometerMagnitude <= WesadThresholds.ACC_MOVEMENT_THRESHOLD

        // Calibración incremental
        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
        }

        // Inferencia basada en el modelo
        val zHr    = hrBaseline.zScore(meanHr)
        val zRmssd = hrvBaseline.zScore(rmssd)

        // El Score Combinado es el promedio de la activación (Z-HR) y la pérdida de HRV (-Z-RMSSD)
        val zCombined = (zHr + (-zRmssd)) / 2.0

        val hrFired       = zHr > WesadThresholds.Z_HR_TRIGGER
        val hrvFired      = zRmssd < WesadThresholds.Z_RMSSD_TRIGGER
        val combinedFired = zCombined > WesadThresholds.Z_COMBINED_TRIGGER

        // Decisión final: el modelo es positivo si el combinado dispara o ambos individuales disparan
        val windowPositive = (combinedFired || (hrFired && hrvFired)) && movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val isCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        return DetectionResult(
            isCrisisDetected     = isCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            movementMagnitude    = accelerometerMagnitude,
            hrThresholdExceeded  = hrFired,
            hrvThresholdExceeded = hrvFired,
            movementFilterPassed = movementFilterPassed
        )
    }

    fun resetConsecutiveCount() { consecutivePositiveWindows = 0 }
    fun getCalibrationStatus(): String =
        "$calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas calibradas"
    fun isCalibrated(): Boolean =
        calibrationWindows >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    suspend fun loadBaseline(repository: BaselineRepository) {
        val stats = repository.load() ?: return
        hrBaseline.restore(stats.hrCount, stats.hrMean, stats.hrM2)
        hrvBaseline.restore(stats.hrvCount, stats.hrvMean, stats.hrvM2)
        calibrationWindows = stats.calibrationWindows
        Log.d("CrisisDetector", "Baseline restaurado: ${getCalibrationStatus()}")
    }

    suspend fun saveBaseline(repository: BaselineRepository) {
        repository.save(
            hrCount            = hrBaseline.count,
            hrMean             = hrBaseline.mean,
            hrM2               = hrBaseline.m2,
            hrvCount           = hrvBaseline.count,
            hrvMean            = hrvBaseline.mean,
            hrvM2              = hrvBaseline.m2,
            calibrationWindows = calibrationWindows
        )
    }
}

class RunningStats(
    private val fallbackMean: Double = 0.0,
    private val fallbackStd: Double  = 1.0
) {
    var count = 0
        private set
    var mean  = 0.0
        private set
    var m2    = 0.0
        private set

    fun add(value: Double) {
        count++
        val delta  = value - mean
        mean      += delta / count
        val delta2 = value - mean
        m2        += delta * delta2
    }

    fun restore(savedCount: Int, savedMean: Double, savedM2: Double) {
        count = savedCount
        mean  = savedMean
        m2    = savedM2
    }

    fun std(): Double = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std()
}