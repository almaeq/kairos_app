package com.example.kairos.detection

import android.util.Log

/**
 * Wrapper Kotlin para el modelo Random Forest exportado desde Python con m2cgen,
 * ejecutándose directamente en el reloj sin dependencias externas.
 *
 * Esta clase es el equivalente Wear OS de `CrisisPredictor` del teléfono.
 * Usa el mismo modelo ([Model.score]) y la misma lógica de normalización,
 * garantizando que la detección en el reloj sea idéntica a la del teléfono.
 *
 * **Features de entrada al modelo:**
 * - `input[0]` = Z-score de HR    → `(hr_actual - hr_baseline) / hr_std`
 * - `input[1]` = Z-score de RMSSD → `(rmssd_baseline - rmssd_actual) / rmssd_std`
 *   El RMSSD está invertido porque en estrés agudo baja, y necesitamos
 *   que un Z positivo alto indique crisis en ambas features.
 *
 * **Output del modelo:**
 * `[votos_baseline, votos_estres]` — suma de votos de los árboles del Random Forest.
 * `prob_crisis = votos_estres / (votos_baseline + votos_estres)`
 *
 * **Umbrales de decisión** (justificados por análisis ROC sobre WESAD):
 * - [THRESHOLD_PRE_ALERT] = 0.55 → muestra la pantalla "¿Estás bien?" en el reloj.
 * - [THRESHOLD_CRISIS]    = 0.75 → activa el Modo Crisis completo (SMS + ejercicios).
 *   A este umbral, Precision = 1.0 y Recall = 0.689 sobre el set de testeo WESAD.
 */
object CrisisPredictor {

    private const val TAG = "CrisisPredictor"

    /** Umbral para activar la pre-alerta en el reloj y esperar confirmación del usuario. */
    const val THRESHOLD_PRE_ALERT = 0.25

    /**
     * Umbral para confirmar la crisis y activar el Modo Crisis completo.
     * Elegido para Precision = 1.0 — cero falsas alarmas a costa de menor recall.
     */
    const val THRESHOLD_CRISIS = 0.3

    /**
     * Resultado de una predicción del modelo Random Forest en el reloj.
     *
     * @property probCrisis Probabilidad de crisis entre 0.0 y 1.0.
     * @property isCrisis `true` si [probCrisis] >= [THRESHOLD_CRISIS].
     * @property isPreAlert `true` si [probCrisis] está entre [THRESHOLD_PRE_ALERT] y [THRESHOLD_CRISIS].
     * @property zHr Z-score de HR respecto al baseline personal.
     * @property zRmssd Z-score de RMSSD respecto al baseline personal (invertido).
     */
    data class PredictionResult(
        val probCrisis: Double,
        val isCrisis:   Boolean,
        val isPreAlert: Boolean,
        val zHr:        Double,
        val zRmssd:     Double
    )

    /**
     * Ejecuta la predicción del modelo Random Forest para una ventana de señales.
     *
     * Calcula Z-scores personalizados con el baseline del usuario, invoca [Model.score]
     * y clasifica el resultado según los umbrales definidos.
     *
     * Los Z-scores usan el baseline personal cuando está disponible,
     * o los parámetros globales WESAD como fallback antes de la calibración.
     *
     * @param hrMean HR media de la ventana actual en BPM.
     * @param rmssd RMSSD de la ventana actual en milisegundos.
     * @param hrBaseline Media personal de HR en reposo (del algoritmo de Welford).
     * @param hrStd Desviación estándar personal de HR en reposo.
     * @param rmssdBaseline Media personal de RMSSD en reposo.
     * @param rmssdStd Desviación estándar personal de RMSSD en reposo.
     * @return [PredictionResult] con la probabilidad de crisis y los flags de clasificación.
     */
    fun predict(
        hrMean:        Double,
        rmssd:         Double,
        hrBaseline:    Double,
        hrStd:         Double,
        rmssdBaseline: Double,
        rmssdStd:      Double
    ): PredictionResult {

        // Z-score de HR: positivo alto → HR elevada respecto al reposo personal → posible crisis
        val zHr = if (hrStd > 0) (hrMean - hrBaseline) / hrStd else 0.0

        // Z-score de RMSSD: invertido — en estrés el RMSSD baja, por lo que
        // (baseline - actual) es positivo y el Z-score apunta en la misma dirección que zHr
        val zRmssd = if (rmssdStd > 0) (rmssdBaseline - rmssd) / rmssdStd else 0.0

        // Invocamos el modelo Random Forest exportado a Java puro por m2cgen
        val input  = doubleArrayOf(zHr, zRmssd)
        val output = Model.score(input)

        // output = [votos_baseline, votos_estres] — normalizamos a probabilidad
        val total      = output[0] + output[1]
        val probCrisis = if (total > 0) output[1] / total else 0.0

        Log.d(TAG, "HR=%.1f zHR=%.2f RMSSD=%.1f zRMSSD=%.2f → p(crisis)=%.3f"
            .format(hrMean, zHr, rmssd, zRmssd, probCrisis))

        return PredictionResult(
            probCrisis = probCrisis,
            isCrisis   = probCrisis >= THRESHOLD_CRISIS,
            isPreAlert = probCrisis >= THRESHOLD_PRE_ALERT && probCrisis < THRESHOLD_CRISIS,
            zHr        = zHr,
            zRmssd     = zRmssd
        )
    }
}