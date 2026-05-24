package com.example.kairos.detection

import android.content.Context
import android.util.Log

class WatchCrisisDetector(private val context: Context) {

    private var consecutivePositiveWindows = 0
    private var hrBaseline  = RunningStats(WesadThresholds.HR_BASELINE_MEAN, WesadThresholds.HR_BASELINE_STD)
    private var hrvBaseline = RunningStats(WesadThresholds.HRV_RMSSD_BASELINE_MEAN, WesadThresholds.HRV_RMSSD_BASELINE_STD)
    var calibrationWindows = 0
        private set

    init { loadBaseline() }

    fun analyze(bpmSamples: List<Double>, stepsInWindow: Long = 0L): WatchDetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(bpmSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(bpmSamples)  ?: return null

        val movementFilterPassed =
            (stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)) <= 30

        // ── Fase de calibración ───────────────────────────────────────────────
        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
            saveBaseline()
            Log.d("WatchDetector", "Calibrando $calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} " +
                    "— HR=${"%.1f".format(meanHr)} RMSSD=${"%.1f".format(rmssd)}")
        }

        // ── Predicción con Random Forest ──────────────────────────────────────
        // Pasamos los valores crudos y el baseline personal;
        // CrisisPredictor calcula los Z-scores internamente antes de llamar al modelo.
        val prediction = CrisisPredictor.predict(
            hrMean        = meanHr,
            rmssd         = rmssd,
            hrBaseline    = hrBaseline.mean,
            hrStd         = hrBaseline.std(),
            rmssdBaseline = hrvBaseline.mean,
            rmssdStd      = hrvBaseline.std()
        )

        // ── Confirmación por ventanas consecutivas ────────────────────────────
        // El RF puede dar falsos positivos en ventanas aisladas.
        // Requerimos CONSECUTIVE_WINDOWS_TO_CONFIRM seguidas para activar crisis.
        val windowPositive = (prediction.isPreAlert || prediction.isCrisis) && movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val confirmedCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        Log.d("WatchDetector", "p(crisis)=${"%.2f".format(prediction.probCrisis)} " +
                "ventanas_positivas=$consecutivePositiveWindows " +
                "crisis=$confirmedCrisis filtro_mov=$movementFilterPassed")

        return WatchDetectionResult(
            isCrisisDetected     = confirmedCrisis,
            isPreAlert           = windowPositive && !confirmedCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            hrThresholdExceeded  = prediction.zHr > 1.0,   // solo informativo
            hrvThresholdExceeded = prediction.zRmssd > 1.0, // solo informativo
            movementFilterPassed = movementFilterPassed,
            calibrationWindows   = calibrationWindows
        )
    }

    fun isCalibrated() = calibrationWindows >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    fun reset() {
        hrBaseline  = RunningStats(WesadThresholds.HR_BASELINE_MEAN, WesadThresholds.HR_BASELINE_STD)
        hrvBaseline = RunningStats(WesadThresholds.HRV_RMSSD_BASELINE_MEAN, WesadThresholds.HRV_RMSSD_BASELINE_STD)
        calibrationWindows         = 0
        consecutivePositiveWindows = 0
        Log.d("WatchDetector", "Detector reseteado — recalibrando desde cero")
    }

    private fun loadBaseline() {
        val data = com.example.kairos.db.WatchBaseline.load(context) ?: return
        hrBaseline.restore(data.hrCount, data.hrMean, data.hrM2)
        hrvBaseline.restore(data.hrvCount, data.hrvMean, data.hrvM2)
        calibrationWindows = data.calibrationWindows
        Log.d("WatchDetector", "Baseline cargado: $calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas")
    }

    private fun saveBaseline() {
        com.example.kairos.db.WatchBaseline.save(
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
        @Volatile private var instance: WatchCrisisDetector? = null

        fun getInstance(context: Context): WatchCrisisDetector {
            return instance ?: synchronized(this) {
                instance ?: WatchCrisisDetector(context.applicationContext).also { instance = it }
            }
        }
    }
}