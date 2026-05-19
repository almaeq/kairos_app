package com.example.kairos.mobile.detection

import android.util.Log

/**
 * Wrapper Kotlin para el Random Forest exportado desde Python con m2cgen.
 *
 * El modelo fue entrenado en WESAD con features normalizadas:
 *   input[0] = Z-score de HR    → (hr_actual - hr_baseline) / hr_std
 *   input[1] = Z-score de RMSSD → (rmssd_baseline - rmssd_actual) / rmssd_std
 *                                   ↑ invertido porque en estrés el RMSSD baja
 *
 * Output: [votos_baseline, votos_estres] — suma de los 21 árboles
 *   → prob_crisis = votos_estres / (votos_baseline + votos_estres)
 */
object CrisisPredictor {

    private const val TAG = "CrisisPredictor"

    const val THRESHOLD_PRE_ALERT = 0.45
    const val THRESHOLD_CRISIS    = 0.65

    data class PredictionResult(
        val probCrisis: Double,
        val isCrisis:   Boolean,
        val isPreAlert: Boolean,
        val zHr:        Double,
        val zRmssd:     Double
    )

    fun predict(
        hrMean:        Double,
        rmssd:         Double,
        hrBaseline:    Double,
        hrStd:         Double,
        rmssdBaseline: Double,
        rmssdStd:      Double
    ): PredictionResult {

        val zHr    = if (hrStd > 0)    (hrMean - hrBaseline) / hrStd         else 0.0
        val zRmssd = if (rmssdStd > 0) (rmssdBaseline - rmssd) / rmssdStd    else 0.0

        val input  = doubleArrayOf(zHr, zRmssd)
        val output = Model.score(input)

        val total      = output[0] + output[1]
        val probCrisis = if (total > 0) output[1] / total else 0.0

        Log.d(TAG, "HR=%.1f zHR=%.2f RMSSD=%.1f zRMSSD=%.2f → p(crisis)=%.3f"
            .format(hrMean, zHr, rmssd, zRmssd, probCrisis))

        return PredictionResult(
            probCrisis  = probCrisis,
            isCrisis    = probCrisis >= THRESHOLD_CRISIS,
            isPreAlert  = probCrisis >= THRESHOLD_PRE_ALERT && probCrisis < THRESHOLD_CRISIS,
            zHr         = zHr,
            zRmssd      = zRmssd
        )
    }
}