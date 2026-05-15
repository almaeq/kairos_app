package com.example.kairos.detection

import android.content.Context
import android.util.Log
import com.example.kairos.db.WatchBaseline

class WatchCrisisDetector(private val context: Context) {

    private var consecutivePositiveWindows = 0
    private var hrBaseline = RunningStats(WesadThresholds.HR_BASELINE_MEAN, WesadThresholds.HR_BASELINE_STD)
    private var hrvBaseline = RunningStats(WesadThresholds.HRV_RMSSD_BASELINE_MEAN, WesadThresholds.HRV_RMSSD_BASELINE_STD)
    var calibrationWindows = 0
        private set

    init { loadBaseline() }

    fun analyze(bpmSamples: List<Double>, stepsInWindow: Long = 0L): WatchDetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(bpmSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(bpmSamples)  ?: return null

        val movementFilterPassed = (stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)) <= 30

        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
            saveBaseline()
        }

        val zHr    = hrBaseline.zScore(meanHr)
        val zRmssd = hrvBaseline.zScore(rmssd)
        val zCombined = (zHr + (-zRmssd)) / 2.0

        val hrExceeded  = zHr > WesadThresholds.Z_HR_TRIGGER
        val hrvExceeded = zRmssd < WesadThresholds.Z_RMSSD_TRIGGER
        val combinedFired = zCombined > WesadThresholds.Z_COMBINED_TRIGGER

        val windowPositive = (combinedFired || (hrExceeded && hrvExceeded)) && movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val isCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        return WatchDetectionResult(
            isCrisisDetected     = isCrisis,
            isPreAlert           = windowPositive && !isCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            hrThresholdExceeded  = hrExceeded,
            hrvThresholdExceeded = hrvExceeded,
            movementFilterPassed = movementFilterPassed,
            calibrationWindows   = calibrationWindows
        )
    }
    fun isCalibrated() = calibrationWindows >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    /**
     * Resetea el estado en memoria del detector.
     * Llamar después de borrar las SharedPreferences para que la
     * calibración empiece desde cero en el próximo ciclo de análisis.
     */
    fun reset() {
        hrBaseline = RunningStats(
            fallbackMean = WesadThresholds.HR_BASELINE_MEAN,
            fallbackStd  = WesadThresholds.HR_BASELINE_STD
        )
        hrvBaseline = RunningStats(
            fallbackMean = WesadThresholds.HRV_RMSSD_BASELINE_MEAN,
            fallbackStd  = WesadThresholds.HRV_RMSSD_BASELINE_STD
        )
        calibrationWindows         = 0
        consecutivePositiveWindows = 0
        Log.d("WatchDetector", "Detector reseteado — recalibrando desde cero")
    }

    private fun loadBaseline() {
        val data = WatchBaseline.load(context) ?: return
        hrBaseline.restore(data.hrCount, data.hrMean, data.hrM2)
        hrvBaseline.restore(data.hrvCount, data.hrvMean, data.hrvM2)
        calibrationWindows = data.calibrationWindows
        Log.d("WatchDetector", "Baseline cargado: $calibrationWindows/3 ventanas")
    }

    private fun saveBaseline() {
        WatchBaseline.save(
            context            = context,
            hrCount            = hrBaseline.count,
            hrMean             = hrBaseline.mean,
            hrM2               = hrBaseline.m2,
            hrvCount           = hrvBaseline.count,
            hrvMean            = hrvBaseline.mean,
            hrvM2              = hrvBaseline.m2,
            calibrationWindows = calibrationWindows
        )
    }

    companion object {
        @Volatile
        private var instance: WatchCrisisDetector? = null

        fun getInstance(context: Context): WatchCrisisDetector {
            return instance ?: synchronized(this) {
                instance ?: WatchCrisisDetector(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}