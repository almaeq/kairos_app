package com.example.kairos.detection

import android.util.Log

/**
 * Wrapper Kotlin para el Random Forest exportado desde Python con m2cgen.
 *
 * El modelo fue entrenado en WESAD con features normalizadas:
 *   input[0] = Z-score de HR    → (hr_actual - hr_baseline) / hr_std
 *   input[1] = Z-score de RMSSD → (rmssd_baseline - rmssd_actual) / rmssd_std
 *                                   ↑ invertido porque en estrés el RMSSD baja
 *
 * Output: [prob_baseline, prob_estres]
 *   → output[1] / (output[0] + output[1]) = probabilidad de crisis (0.0 a 1.0)
 *
 * Umbrales recomendados (ajustables en beta):
 *   CRISIS    : prob_crisis >= 0.65
 *   PRE_ALERTA: prob_crisis >= 0.45
 */
object CrisisPredictor {

    private const val TAG = "CrisisPredictor"

    // Umbral para disparar Pre-Alerta (se muestra en el reloj, espera confirmación)
    const val THRESHOLD_PRE_ALERT = 0.45

    // Umbral para activar Modo Crisis completo (SMS + ejercicios)
    const val THRESHOLD_CRISIS = 0.65

    data class PredictionResult(
        val probCrisis: Double,       // 0.0 a 1.0
        val isCrisis: Boolean,
        val isPreAlert: Boolean,
        val zHr: Double,
        val zRmssd: Double
    )

    /**
     * Predice el estado a partir de valores crudos de HR y RMSSD
     * usando el baseline personal del usuario para normalizar.
     *
     * @param hrMean       HR promedio de la ventana actual (BPM)
     * @param rmssd        RMSSD de la ventana actual (ms)
     * @param hrBaseline   HR promedio del baseline personal
     * @param hrStd        Desviación estándar de HR del baseline
     * @param rmssdBaseline RMSSD promedio del baseline personal
     * @param rmssdStd     Desviación estándar de RMSSD del baseline
     */
    fun predict(
        hrMean: Double,
        rmssd: Double,
        hrBaseline: Double,
        hrStd: Double,
        rmssdBaseline: Double,
        rmssdStd: Double
    ): PredictionResult {

        // Normalizar a Z-scores (igual que en el notebook de entrenamiento)
        val zHr = if (hrStd > 0) (hrMean - hrBaseline) / hrStd else 0.0
        val zRmssd = if (rmssdStd > 0) (rmssdBaseline - rmssd) / rmssdStd else 0.0
        // ^ RMSSD invertido: en estrés baja, así que el Z positivo = más estrés

        val input = doubleArrayOf(zHr, zRmssd)

        // Llamar al modelo exportado por m2cgen
        val output = Model.score(input)

        // output = [votos_baseline, votos_estres] — suma de los 21 árboles
        val total = output[0] + output[1]
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