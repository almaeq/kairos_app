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
        val movementFilterPassed = stepsPerMinute <= 30 &&
                accelerometerMagnitude <= WesadThresholds.ACC_MOVEMENT_THRESHOLD

        // ── Calibración incremental ───────────────────────────────────────────
        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
        }

        // ── Predicción con Random Forest ──────────────────────────────────────
        // CrisisPredictor normaliza internamente con el baseline personal
        // y devuelve prob(crisis) entre 0.0 y 1.0
        val prediction = CrisisPredictor.predict(
            hrMean        = meanHr,
            rmssd         = rmssd,
            hrBaseline    = hrBaseline.mean,
            hrStd         = hrBaseline.std,
            rmssdBaseline = hrvBaseline.mean,
            rmssdStd      = hrvBaseline.std
        )

        // ── Confirmación por ventanas consecutivas ────────────────────────────
        val windowPositive = (prediction.isPreAlert || prediction.isCrisis) && movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val confirmedCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        Log.d("CrisisDetector", "p(crisis)=${"%.2f".format(prediction.probCrisis)} " +
                "ventanas_positivas=$consecutivePositiveWindows crisis=$confirmedCrisis")

        return DetectionResult(
            isCrisisDetected     = confirmedCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            movementMagnitude    = accelerometerMagnitude,
            hrThresholdExceeded  = prediction.zHr > 1.0,    // solo informativo
            hrvThresholdExceeded = prediction.zRmssd > 1.0, // solo informativo
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

    val std: Double get() = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

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

    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std
}