package com.example.kairos.mobile.detection

import android.util.Log

/**
 * Wrapper Kotlin para el modelo Random Forest exportado desde Python con m2cgen.
 *
 * Encapsula la lógica de normalización (Z-scores) y la interpretación del output
 * del modelo, exponiendo una interfaz simple para el resto del sistema.
 *
 * **Pipeline de predicción:**
 * ```
 * HR + RMSSD → Z-scores personalizados → Model.score() → prob(crisis) → umbral
 * ```
 *
 * **Features de entrada al modelo:**
 * - `input[0]` = Z-score de HR    → `(hr_actual - hr_baseline) / hr_std`
 * - `input[1]` = Z-score de RMSSD → `(rmssd_baseline - rmssd_actual) / rmssd_std`
 *   El RMSSD está invertido porque en estrés agudo el RMSSD *baja*,
 *   por lo que un valor negativo de RMSSD produce un Z-score positivo
 *   (alineado con la dirección de detección de crisis).
 *
 * **Output del modelo:**
 * `[votos_baseline, votos_estres]` — suma de votos de los árboles del Random Forest.
 * La probabilidad de crisis se calcula como:
 * `prob_crisis = votos_estres / (votos_baseline + votos_estres)`
 *
 * **Umbrales de decisión** (justificados por análisis de curva ROC sobre WESAD):
 * - [THRESHOLD_PRE_ALERT] = 0.55 → activa la pre-alerta ("¿Estás bien?")
 * - [THRESHOLD_CRISIS]    = 0.75 → confirma la crisis y activa el Modo Crisis.
 *   A este umbral, Precision = 1.0 y Recall = 0.689 sobre el set de testeo WESAD,
 *   priorizando la eliminación de falsas alarmas.
 */
object CrisisPredictor {

    private const val TAG = "CrisisPredictor"

    /** Umbral de probabilidad para activar la pantalla de pre-alerta. */
    const val THRESHOLD_PRE_ALERT = 0.55

    /**
     * Umbral de probabilidad para confirmar una crisis y activar el Modo Crisis.
     *
     * Elegido en base al análisis de umbrales sobre WESAD:
     * a 0.75, Precision = 1.0 (cero falsas alarmas) con Recall = 0.689.
     * Para KAIROS, eliminar las falsas alarmas es prioritario sobre maximizar el recall,
     * ya que una falsa alarma puede generar desconfianza en el sistema.
     */
    const val THRESHOLD_CRISIS = 0.75

    /**
     * Resultado de una predicción del modelo Random Forest.
     *
     * @property probCrisis Probabilidad de crisis entre 0.0 y 1.0.
     * @property isCrisis `true` si [probCrisis] >= [THRESHOLD_CRISIS].
     * @property isPreAlert `true` si [probCrisis] está entre [THRESHOLD_PRE_ALERT]
     *           y [THRESHOLD_CRISIS] (zona de alerta temprana).
     * @property zHr Z-score de HR respecto al baseline personal del usuario.
     *           Valor positivo indica HR elevada respecto al reposo habitual.
     * @property zRmssd Z-score de RMSSD respecto al baseline personal del usuario.
     *           Valor positivo indica RMSSD bajo respecto al reposo habitual (invertido).
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
     * Calcula los Z-scores personalizados, invoca [Model.score] con el código Java
     * generado por m2cgen, y clasifica el resultado según los umbrales definidos.
     *
     * Los Z-scores usan los parámetros personales del usuario cuando está calibrado,
     * o los parámetros globales del dataset WESAD como fallback antes de la calibración.
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

        // Z-score de HR: cuántas desviaciones estándar está la HR actual
        // por encima de la HR personal de reposo
        val zHr = if (hrStd > 0) (hrMean - hrBaseline) / hrStd else 0.0

        // Z-score de RMSSD: invertido intencionalmente
        // En estrés el RMSSD baja → baseline - actual es positivo → z positivo
        val zRmssd = if (rmssdStd > 0) (rmssdBaseline - rmssd) / rmssdStd else 0.0

        // Invocamos el modelo Random Forest exportado a Java puro por m2cgen
        // Model.score() implementa la lógica completa del árbol sin dependencias externas
        val input  = doubleArrayOf(zHr, zRmssd)
        val output = Model.score(input)

        // output = [votos_baseline, votos_estres]
        // Convertimos a probabilidad normalizando sobre el total de votos
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