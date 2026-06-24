package com.example.kairos.detection

import android.content.Context
import android.util.Log

/**
 * Detector de crisis de ansiedad que corre directamente en el reloj (Wear OS).
 *
 * Equivalente Wear OS de `CrisisDetector` del teléfono, adaptado para el entorno
 * del smartwatch donde los datos provienen de `PassiveMonitoringClient` en lugar
 * de Health Connect.
 *
 * Implementa el mismo pipeline de tres capas que el teléfono:
 * 1. **Calibración incremental:** acumula ventanas de baseline con [RunningStats]
 *    (algoritmo de Welford) y persiste los parámetros en [WatchBaseline] tras cada ventana.
 * 2. **Predicción:** delega en [CrisisPredictor] (Random Forest via m2cgen).
 * 3. **Confirmación por ventanas consecutivas:** evita falsos positivos aislados.
 *
 * **Patrón Singleton:**
 * Es singleton porque `PassiveListenerService` es instanciado y destruido repetidamente
 * por el SO. El estado de calibración y el contador de ventanas consecutivas deben
 * persistir entre esos ciclos — de ahí el `companion object` con double-checked locking.
 *
 * El baseline se carga desde [WatchBaseline] (SharedPreferences) en el `init`,
 * garantizando que el detector recupere su estado tras un reinicio del servicio.
 */
class WatchCrisisDetector(private val context: Context) {

    /**
     * Contador de ventanas consecutivas que superaron el umbral de detección.
     * Se resetea a 0 cuando una ventana no supera el umbral o cuando el usuario
     * cancela la pre-alerta ([onUserCancelled]).
     */
    private var consecutivePositiveWindows = 0

    /**
     * Estadísticas incrementales de HR con fallback a los valores globales WESAD.
     * Se reemplaza por parámetros personales una vez completada la calibración.
     */
    private var hrBaseline  = RunningStats(WesadThresholds.HR_BASELINE_MEAN, WesadThresholds.HR_BASELINE_STD)

    /**
     * Estadísticas incrementales de HRV (RMSSD) con fallback a los valores globales WESAD.
     */
    private var hrvBaseline = RunningStats(WesadThresholds.HRV_RMSSD_BASELINE_MEAN, WesadThresholds.HRV_RMSSD_BASELINE_STD)

    /**
     * Número de ventanas de baseline completadas.
     * Cuando alcanza [WesadThresholds.MIN_CALIBRATION_WINDOWS], el detector
     * usa el baseline personal en lugar de los umbrales globales WESAD.
     */
    var calibrationWindows = 0
        private set

    init {
        // Restauramos el baseline desde SharedPreferences al crear la instancia
        loadBaseline()
    }

    /**
     * Analiza una ventana de muestras de BPM y determina si hay indicios de crisis.
     *
     * Ejecuta el pipeline completo: extracción de features → filtro de movimiento →
     * calibración incremental → predicción con Random Forest → confirmación por ventanas.
     *
     * @param bpmSamples Lista de muestras de HR en BPM de la ventana actual (típicamente 60s).
     * @param stepsInWindow Total de pasos registrados durante la ventana.
     *        Se usa para filtrar taquicardia por ejercicio físico.
     * @return [WatchDetectionResult] con el resultado del análisis, o `null` si no hay
     *         suficientes muestras para calcular HR o RMSSD.
     */
    fun analyze(bpmSamples: List<Double>, stepsInWindow: Long = 0L): WatchDetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(bpmSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(bpmSamples)  ?: return null

        // El filtro de movimiento pasa (true) cuando el usuario está en reposo:
        // menos de 30 pasos/minuto indica que la taquicardia no es por ejercicio
        val movementFilterPassed =
            (stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)) <= 30

        // ── Calibración incremental (algoritmo de Welford) ────────────────────
        // Solo acumulamos ventanas de baseline cuando el usuario está en reposo
        // y todavía no se alcanzó el mínimo de ventanas requeridas
        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
            // Persistimos después de cada ventana para sobrevivir reinicios del servicio
            saveBaseline()
            Log.d("WatchDetector", "Calibrando $calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} " +
                    "— HR=${"%.1f".format(meanHr)} RMSSD=${"%.1f".format(rmssd)}")
        }

        // ── Predicción con Random Forest ──────────────────────────────────────
        // CrisisPredictor calcula los Z-scores con el baseline personal internamente
        val prediction = CrisisPredictor.predict(
            hrMean        = meanHr,
            rmssd         = rmssd,
            hrBaseline    = hrBaseline.mean,
            hrStd         = hrBaseline.std(),
            rmssdBaseline = hrvBaseline.mean,
            rmssdStd      = hrvBaseline.std()
        )

        // ── Confirmación por ventanas consecutivas ────────────────────────────
        // Una sola ventana positiva puede ser un artefacto — exigimos N ventanas
        // consecutivas para confirmar la crisis y evitar falsas alarmas
        val windowPositive = (prediction.isPreAlert || prediction.isCrisis) && movementFilterPassed

        if (windowPositive) consecutivePositiveWindows++
        else consecutivePositiveWindows = 0

        val confirmedCrisis = consecutivePositiveWindows >= WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM

        Log.d("WatchDetector", "p(crisis)=${"%.2f".format(prediction.probCrisis)} " +
                "ventanas_positivas=$consecutivePositiveWindows " +
                "crisis=$confirmedCrisis sin_movimiento_excesivo=$movementFilterPassed")

        return WatchDetectionResult(
            isCrisisDetected     = confirmedCrisis,
            // isPreAlert es true cuando la ventana supera el umbral pero aún no se confirmó la crisis
            isPreAlert           = windowPositive && !confirmedCrisis,
            averageHrBpm         = meanHr,
            rmssdMs              = rmssd,
            hrThresholdExceeded  = prediction.zHr > 1.0,    // informativo — no determina la crisis
            hrvThresholdExceeded = prediction.zRmssd > 1.0, // informativo — no determina la crisis
            movementFilterPassed = movementFilterPassed,
            calibrationWindows   = calibrationWindows
        )
    }

    /**
     * Indica si el detector completó el mínimo de ventanas de calibración personal.
     *
     * @return `true` si se completaron [WesadThresholds.MIN_CALIBRATION_WINDOWS] ventanas.
     */
    fun isCalibrated() = calibrationWindows >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    /**
     * Resetea completamente el estado del detector a sus valores iniciales.
     *
     * Se invoca cuando [WatchBaseline.clear] borra el baseline — el detector
     * vuelve a usar los umbrales globales WESAD y comienza a recalibrar.
     */
    fun reset() {
        hrBaseline  = RunningStats(WesadThresholds.HR_BASELINE_MEAN, WesadThresholds.HR_BASELINE_STD)
        hrvBaseline = RunningStats(WesadThresholds.HRV_RMSSD_BASELINE_MEAN, WesadThresholds.HRV_RMSSD_BASELINE_STD)
        calibrationWindows         = 0
        consecutivePositiveWindows = 0
        Log.d("WatchDetector", "Detector reseteado — recalibrando desde cero")
    }

    /**
     * Restaura el baseline personal desde [WatchBaseline] (SharedPreferences).
     *
     * Se llama en el `init` para recuperar el estado de calibración tras un
     * reinicio del servicio sin perder las ventanas acumuladas en sesiones anteriores.
     */
    private fun loadBaseline() {
        val data = com.example.kairos.db.WatchBaseline.load(context) ?: return
        hrBaseline.restore(data.hrCount, data.hrMean, data.hrM2)
        hrvBaseline.restore(data.hrvCount, data.hrvMean, data.hrvM2)
        calibrationWindows = data.calibrationWindows
        Log.d("WatchDetector", "Baseline cargado: $calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas")
    }

    /**
     * Persiste el estado actual del baseline en [WatchBaseline] (SharedPreferences).
     *
     * Se llama después de cada ventana de calibración para garantizar que los
     * parámetros de Welford sobrevivan reinicios del servicio o del dispositivo.
     */
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

    /**
     * Resetea el contador de ventanas consecutivas cuando el usuario cancela la pre-alerta.
     *
     * Evita que el sistema active la crisis inmediatamente después de una cancelación
     * — el usuario señaló que es un falso positivo, por lo que reiniciamos la cuenta.
     */
    fun onUserCancelled() {
        consecutivePositiveWindows = 0
        Log.d("WatchDetector", "Usuario canceló — ventanas consecutivas reseteadas")
    }

    companion object {
        /**
         * Instancia única del detector.
         *
         * [@Volatile] garantiza visibilidad inmediata entre hilos.
         * Necesario porque `PassiveListenerService` es creado y destruido repetidamente
         * por el SO — el singleton preserva el estado de calibración entre esos ciclos.
         */
        @Volatile private var instance: WatchCrisisDetector? = null

        /**
         * Retorna la instancia única del detector, creándola si no existe.
         *
         * Usa `applicationContext` para evitar memory leaks por retención de Service contexts.
         * Double-checked locking garantiza thread-safety sin sincronización innecesaria
         * en el caso común donde la instancia ya existe.
         *
         * @param context Contexto para inicializar el detector y acceder a SharedPreferences.
         * @return Instancia única de [WatchCrisisDetector].
         */
        fun getInstance(context: Context): WatchCrisisDetector {
            return instance ?: synchronized(this) {
                instance ?: WatchCrisisDetector(context.applicationContext).also { instance = it }
            }
        }
    }
}