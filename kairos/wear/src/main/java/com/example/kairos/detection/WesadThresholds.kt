package com.example.kairos.detection

/**
 * Constantes de detección de KAIROS derivadas del entrenamiento sobre el dataset WESAD.
 *
 * Archivo compartido entre el módulo del reloj (Wear OS) y el módulo del teléfono (Android).
 * Todos los valores tienen justificación empírica documentada en el Notebook 01
 * (exploración WESAD) y en el análisis de umbrales de la tesis.
 *
 * **Rendimiento del modelo validado sobre sujetos de testeo (hold-out set):**
 * - Recall:    0.689 — detecta el 68.9% de las crisis reales
 * - Precision: 1.000 — cero falsas alarmas con umbral 0.75
 * - Accuracy:  0.82  — clasificación correcta en el 82% de las ventanas
 *
 * Fuente: `kairos_model_final.pkl` entrenado con GridSearchCV + LOGO-CV sobre
 * 12 sujetos WESAD, evaluado en 3 sujetos de testeo nunca vistos durante el entrenamiento.
 */
object WesadThresholds {

    // ── Referencias estadísticas WESAD (fallback antes de calibración personal) ─

    /**
     * Media global de HR en estado basal sobre 15 sujetos WESAD.
     * Usado como fallback en [RunningStats] antes de completar la calibración personal.
     * Unidad: BPM.
     */
    const val HR_BASELINE_MEAN = 72.4

    /**
     * Desviación estándar global de HR en estado basal sobre 15 sujetos WESAD.
     * Usado como fallback en [RunningStats] para el cálculo de Z-scores iniciales.
     * Unidad: BPM.
     */
    const val HR_BASELINE_STD = 8.0

    /**
     * Media global de RMSSD en estado basal sobre 15 sujetos WESAD.
     * Usado como fallback en [RunningStats] antes de completar la calibración personal.
     * Unidad: milisegundos.
     */
    const val HRV_RMSSD_BASELINE_MEAN = 51.0

    /**
     * Desviación estándar global de RMSSD en estado basal sobre 15 sujetos WESAD.
     * Usado como fallback en [RunningStats] para el cálculo de Z-scores iniciales.
     * Unidad: milisegundos.
     */
    const val HRV_RMSSD_BASELINE_STD = 12.0

    // ── Configuración del pipeline de detección ───────────────────────────────

    /**
     * Umbral de magnitud del acelerómetro para el filtro de movimiento.
     *
     * Si la magnitud del vector ACC supera este valor, la ventana se considera
     * afectada por movimiento físico activo y no se usa para detección ni calibración.
     * Evita que la taquicardia por ejercicio se confunda con una crisis de ansiedad.
     * Unidad: g (aceleración gravitacional).
     */
    const val ACC_MOVEMENT_THRESHOLD = 0.12

    /**
     * Duración de cada ventana de análisis en segundos.
     *
     * Ventanas de 60 segundos son el estándar en la literatura de HRV y coinciden
     * con la configuración del modelo entrenado en WESAD.
     * Unidad: segundos.
     */
    const val ANALYSIS_WINDOW_SECONDS = 60L

    /**
     * Número mínimo de ventanas de baseline requeridas para completar la calibración personal.
     *
     * Con 3 ventanas de 60 segundos (3 minutos en total), el algoritmo de Welford
     * tiene suficientes muestras para estimar media y desviación estándar personal
     * con representatividad estadística básica.
     */
    const val MIN_CALIBRATION_WINDOWS = 3

    /**
     * Número de ventanas consecutivas positivas requeridas para confirmar una crisis.
     *
     * Exigir 3 ventanas consecutivas (~3 minutos de señal sostenida) reduce
     * significativamente los falsos positivos por picos transitorios de HR.
     * Una crisis de ansiedad real presenta activación fisiológica sostenida,
     * no un pico aislado.
     */
    const val CONSECUTIVE_WINDOWS_TO_CONFIRM = 3
}