package com.example.kairos.mobile.detection

import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import com.example.kairos.mobile.data.BaselineRepository
import kotlin.math.sqrt

/**
 * Detector de crisis de ansiedad basado en señales fisiológicas del smartwatch.
 *
 * Implementa un pipeline de detección en tres capas:
 * 1. **Calibración incremental:** acumula ventanas de baseline personal usando [RunningStats]
 *    (algoritmo de Welford) para calcular Z-scores personalizados.
 * 2. **Predicción:** delega en [CrisisPredictor] (Random Forest exportado via m2cgen)
 *    que devuelve la probabilidad de crisis normalizada con el baseline personal.
 * 3. **Confirmación por ventanas consecutivas:** evita falsos positivos exigiendo que
 *    la detección se sostenga durante [WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM]
 *    ventanas consecutivas antes de confirmar la crisis.
 *
 * Esta clase se ejecuta tanto en el teléfono (calibración via Health Connect)
 * como en el reloj (detección en tiempo real via PassiveMonitoringClient).
 */
class CrisisDetector {

    /**
     * Contador de ventanas consecutivas que superaron el umbral de detección.
     * Se resetea a 0 cuando una ventana no supera el umbral o cuando el usuario
     * cancela la pre-alerta.
     */
    private var consecutivePositiveWindows = 0

    /**
     * Estadísticas incrementales de HR usando el algoritmo de Welford.
     * Los valores de fallback corresponden a los umbrales globales del dataset WESAD
     * y se usan hasta que se completen [WesadThresholds.MIN_CALIBRATION_WINDOWS] ventanas.
     */
    private val hrBaseline = RunningStats(
        fallbackMean = WesadThresholds.HR_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HR_BASELINE_STD
    )

    /**
     * Estadísticas incrementales de HRV (RMSSD) usando el algoritmo de Welford.
     * Los valores de fallback corresponden a los umbrales globales del dataset WESAD.
     */
    private val hrvBaseline = RunningStats(
        fallbackMean = WesadThresholds.HRV_RMSSD_BASELINE_MEAN,
        fallbackStd  = WesadThresholds.HRV_RMSSD_BASELINE_STD
    )

    /** Número de ventanas de baseline acumuladas para la calibración personal. */
    private var calibrationWindows = 0

    /**
     * Analiza una ventana de señales fisiológicas y determina si hay indicios de crisis.
     *
     * Ejecuta el pipeline completo: extracción de features → filtro de movimiento →
     * calibración incremental → predicción con Random Forest → confirmación por ventanas.
     *
     * @param hrSamples Muestras de HR de la ventana actual (típicamente 60 segundos).
     * @param stepsInWindow Total de pasos registrados durante la ventana,
     *        usado para estimar actividad física y filtrar falsos positivos por ejercicio.
     * @param accelerometerMagnitude Magnitud del vector de aceleración del reloj.
     *        Valor bajo (~0.01) indica reposo; valor alto indica movimiento activo.
     * @return [DetectionResult] con el resultado del análisis, o `null` si no hay
     *         suficientes muestras para calcular HR o RMSSD.
     */
    fun analyze(
        hrSamples: List<HeartRateRecord.Sample>,
        stepsInWindow: Long,
        accelerometerMagnitude: Double = 0.0
    ): DetectionResult? {
        val meanHr = HrvCalculator.calculateMeanHr(hrSamples) ?: return null
        val rmssd  = HrvCalculator.calculateRmssd(hrSamples)  ?: return null

        // Convertimos pasos totales a pasos por minuto para comparar con el umbral
        val stepsPerMinute = stepsInWindow / (WesadThresholds.ANALYSIS_WINDOW_SECONDS / 60.0)

        // El filtro de movimiento pasa (true) cuando el usuario está en reposo:
        // - Menos de 30 pasos/minuto (caminata lenta o quieto)
        // - Acelerómetro por debajo del umbral de movimiento activo
        // filtro_mov=true significa que el movimiento NO invalida la detección
        val movementFilterPassed = stepsPerMinute <= 30 &&
                accelerometerMagnitude <= WesadThresholds.ACC_MOVEMENT_THRESHOLD

        // ── Calibración incremental (algoritmo de Welford) ────────────────────
        // Solo acumulamos ventanas de baseline cuando el usuario está en reposo
        // y todavía no se alcanzó el mínimo de ventanas de calibración requeridas
        if (movementFilterPassed && calibrationWindows < WesadThresholds.MIN_CALIBRATION_WINDOWS) {
            hrBaseline.add(meanHr)
            hrvBaseline.add(rmssd)
            calibrationWindows++
        }

        // ── Predicción con Random Forest ──────────────────────────────────────
        // CrisisPredictor normaliza HR y RMSSD con el baseline personal (Z-scores)
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
        // Una sola ventana positiva puede ser un artefacto — exigimos que la
        // detección se sostenga durante N ventanas consecutivas para confirmar la crisis
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
            hrThresholdExceeded  = prediction.zHr > 1.0,    // informativo — no determina la crisis
            hrvThresholdExceeded = prediction.zRmssd > 1.0, // informativo — no determina la crisis
            movementFilterPassed = movementFilterPassed
        )
    }

    /**
     * Resetea el contador de ventanas consecutivas positivas.
     *
     * Se invoca cuando el usuario cancela la pre-alerta, indicando que la detección
     * fue un falso positivo. Evita que el sistema active la crisis inmediatamente
     * después de una cancelación.
     */
    fun resetConsecutiveCount() { consecutivePositiveWindows = 0 }

    /**
     * Retorna un string descriptivo del estado actual de calibración.
     *
     * @return Texto con el formato "N/M ventanas calibradas".
     */
    fun getCalibrationStatus(): String =
        "$calibrationWindows/${WesadThresholds.MIN_CALIBRATION_WINDOWS} ventanas calibradas"

    /**
     * Indica si el detector completó el mínimo de ventanas de calibración personal.
     *
     * Mientras no esté calibrado, el detector usa los umbrales globales del
     * dataset WESAD como fallback.
     *
     * @return `true` si se completaron [WesadThresholds.MIN_CALIBRATION_WINDOWS] ventanas.
     */
    fun isCalibrated(): Boolean =
        calibrationWindows >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    /**
     * Restaura el baseline personal desde la base de datos Room.
     *
     * Se invoca al iniciar la app para recuperar el estado de calibración
     * de sesiones anteriores, evitando que el usuario tenga que recalibrar
     * cada vez que abre KAIROS.
     *
     * @param repository Repositorio desde el que cargar los parámetros de Welford.
     */
    suspend fun loadBaseline(repository: BaselineRepository) {
        val stats = repository.load() ?: return
        hrBaseline.restore(stats.hrCount, stats.hrMean, stats.hrM2)
        hrvBaseline.restore(stats.hrvCount, stats.hrvMean, stats.hrvM2)
        calibrationWindows = stats.calibrationWindows
        Log.d("CrisisDetector", "Baseline restaurado: ${getCalibrationStatus()}")
    }

    /**
     * Persiste el estado actual del baseline personal en la base de datos Room.
     *
     * Se invoca después de cada ventana de calibración para garantizar que
     * los parámetros de Welford sobrevivan reinicios de la app o del dispositivo.
     *
     * @param repository Repositorio donde guardar los parámetros de Welford.
     */
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

/**
 * Implementación del algoritmo de Welford para cálculo incremental de media y varianza.
 *
 * Permite actualizar la media y la desviación estándar muestra a muestra,
 * sin necesidad de almacenar el historial completo de valores.
 * Esto es esencial para la calibración continua en el smartwatch, donde la memoria
 * y el almacenamiento son recursos limitados.
 *
 * Mientras `count < 2` (menos de 2 muestras), usa los valores de fallback
 * provistos en el constructor para evitar división por cero en el cálculo de Z-scores.
 *
 * @property fallbackMean Media a usar cuando no hay suficientes muestras personales.
 *           Corresponde a la media global del dataset WESAD para esa feature.
 * @property fallbackStd Desviación estándar a usar cuando no hay suficientes muestras.
 *           Corresponde a la std global del dataset WESAD para esa feature.
 */
class RunningStats(
    private val fallbackMean: Double = 0.0,
    private val fallbackStd: Double  = 1.0
) {
    /** Cantidad de muestras acumuladas hasta el momento. */
    var count = 0
        private set

    /** Media acumulada calculada de forma incremental. */
    var mean  = 0.0
        private set

    /**
     * Suma de diferencias al cuadrado respecto a la media (M2 de Welford).
     * La varianza muestral se obtiene como `m2 / (count - 1)`.
     */
    var m2    = 0.0
        private set

    /**
     * Desviación estándar muestral calculada a partir de M2.
     * Retorna [fallbackStd] si hay menos de 2 muestras.
     */
    val std: Double get() = if (count < 2) fallbackStd else sqrt(m2 / (count - 1))

    /**
     * Agrega una nueva muestra y actualiza media y M2 de forma incremental.
     *
     * Implementa el algoritmo de Welford en una sola pasada:
     * - delta  = valor - media_anterior
     * - media += delta / count
     * - delta2 = valor - media_nueva
     * - m2    += delta * delta2
     *
     * @param value Nueva muestra a incorporar al cálculo.
     */
    fun add(value: Double) {
        count++
        val delta  = value - mean
        mean      += delta / count
        val delta2 = value - mean
        m2        += delta * delta2
    }

    /**
     * Restaura el estado interno desde valores previamente guardados en Room.
     *
     * Permite retomar el cálculo incremental desde donde se dejó en una sesión anterior,
     * sin necesidad de recalibrar desde cero.
     *
     * @param savedCount Cantidad de muestras acumuladas en la sesión anterior.
     * @param savedMean Media acumulada guardada.
     * @param savedM2 M2 acumulada guardada.
     */
    fun restore(savedCount: Int, savedMean: Double, savedM2: Double) {
        count = savedCount
        mean  = savedMean
        m2    = savedM2
    }

    /**
     * Calcula el Z-score de un valor respecto a la distribución acumulada.
     *
     * Si hay menos de 2 muestras personales, usa los parámetros de fallback
     * (umbrales globales WESAD) para evitar Z-scores no representativos.
     *
     * @param value Valor a normalizar.
     * @return Z-score: cuántas desviaciones estándar se aleja [value] de la media.
     *         Positivo = por encima de la media; negativo = por debajo.
     */
    fun zScore(value: Double): Double =
        if (count < 2) (value - fallbackMean) / fallbackStd
        else           (value - mean) / std
}