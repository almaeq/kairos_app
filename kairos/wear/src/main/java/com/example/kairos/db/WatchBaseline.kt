package com.example.kairos.db

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kairos.services.KairosWatchService
import com.example.kairos.detection.WatchCrisisDetector
import com.example.kairos.detection.WesadThresholds
import com.example.kairos.ui.WatchMonitorState

/**
 * Repositorio singleton que persiste los parámetros del algoritmo de Welford
 * en SharedPreferences del reloj.
 *
 * A diferencia del teléfono (que usa Room), el reloj usa SharedPreferences
 * porque no tiene acceso a Room y necesita una solución más liviana
 * compatible con el entorno Wear OS.
 *
 * **Nota sobre precisión de Float:**
 * Los valores se guardan como Float (32 bits) en lugar de Double (64 bits)
 * por limitación de la API de SharedPreferences. Para los rangos de HR (~50-200 BPM)
 * y RMSSD (~10-100 ms), la pérdida de precisión es aceptable.
 */
object WatchBaseline {

    private const val PREFS = "kairos_baseline"

    /**
     * Persiste los parámetros actuales del algoritmo de Welford en SharedPreferences.
     *
     * Se invoca después de cada ventana de calibración para garantizar que los
     * parámetros sobrevivan reinicios del servicio o del dispositivo.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @param hrCount Cantidad de muestras de HR acumuladas.
     * @param hrMean Media acumulada de HR (algoritmo de Welford).
     * @param hrM2 Suma de diferencias al cuadrado de HR (para derivar la varianza).
     * @param hrvCount Cantidad de muestras de HRV acumuladas.
     * @param hrvMean Media acumulada de HRV (algoritmo de Welford).
     * @param hrvM2 Suma de diferencias al cuadrado de HRV (para derivar la varianza).
     * @param calibrationWindows Número de ventanas de baseline completadas.
     */
    fun save(
        context: Context,
        hrCount: Int, hrMean: Double, hrM2: Double,
        hrvCount: Int, hrvMean: Double, hrvM2: Double,
        calibrationWindows: Int
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("hr_count", hrCount)
            putFloat("hr_mean", hrMean.toFloat())
            putFloat("hr_m2", hrM2.toFloat())
            putInt("hrv_count", hrvCount)
            putFloat("hrv_mean", hrvMean.toFloat())
            putFloat("hrv_m2", hrvM2.toFloat())
            putInt("cal_windows", calibrationWindows)
            apply()
        }
    }

    /**
     * Carga los parámetros de baseline desde SharedPreferences.
     *
     * Retorna `null` si el usuario no completó el mínimo de ventanas de calibración
     * requeridas ([WesadThresholds.MIN_CALIBRATION_WINDOWS]), indicando que el detector
     * debe usar los umbrales globales WESAD como fallback.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @return [BaselineData] con los parámetros de Welford, o `null` si no calibrado.
     */
    fun load(context: Context): BaselineData? {
        val prefs   = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val windows = prefs.getInt("cal_windows", 0)
        if (windows < WesadThresholds.MIN_CALIBRATION_WINDOWS) return null
        return BaselineData(
            hrCount            = prefs.getInt("hr_count", 0),
            hrMean             = prefs.getFloat("hr_mean", 0f).toDouble(),
            hrM2               = prefs.getFloat("hr_m2", 0f).toDouble(),
            hrvCount           = prefs.getInt("hrv_count", 0),
            hrvMean            = prefs.getFloat("hrv_mean", 0f).toDouble(),
            hrvM2              = prefs.getFloat("hrv_m2", 0f).toDouble(),
            calibrationWindows = windows
        )
    }

    /**
     * Verifica si el usuario completó el mínimo de ventanas de calibración.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @return `true` si hay al menos [WesadThresholds.MIN_CALIBRATION_WINDOWS] ventanas completadas.
     */
    fun isCalibrated(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("cal_windows", 0) >= WesadThresholds.MIN_CALIBRATION_WINDOWS

    /**
     * Borra el baseline y resetea todos los componentes del sistema de detección.
     *
     * Ejecuta cuatro pasos en orden para garantizar consistencia total del estado:
     * 1. Borra los parámetros de Welford de SharedPreferences.
     * 2. Resetea el estado en memoria de [WatchCrisisDetector] (singleton).
     * 3. Resetea el estado observable de la UI ([WatchMonitorState]).
     * 4. Envía un Intent al servicio [KairosWatchService] para resetear el timestamp
     *    de ventana, evitando que el servicio procese datos de una ventana anterior.
     *
     * Se invoca cuando el teléfono envía el mensaje `/kairos/reset_baseline` al reloj.
     *
     * @param context Contexto para acceder a SharedPreferences y lanzar el Intent al servicio.
     */
    fun clear(context: Context) {
        // Paso 1: borrar SharedPreferences
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()

        // Paso 2: resetear el detector en memoria (evita que use parámetros obsoletos)
        WatchCrisisDetector.getInstance(context).reset()

        // Paso 3: resetear el estado observable de la UI del reloj
        WatchMonitorState.resetFull()

        // Paso 4: resetear el timestamp de ventana en el servicio para que
        // la próxima ventana comience desde cero
        val intent = Intent(context, KairosWatchService::class.java).apply {
            action = KairosWatchService.ACTION_RESET_WINDOW
        }
        context.startService(intent)

        Log.d("WatchBaseline", "Baseline borrado — listo para recalibrar")
    }
}

/**
 * Modelo de datos que encapsula los parámetros del algoritmo de Welford
 * almacenados en SharedPreferences del reloj.
 *
 * Equivalente a [BaselineStats] en el teléfono, pero sin Room.
 *
 * @property hrCount Cantidad de muestras de HR acumuladas.
 * @property hrMean Media acumulada de HR.
 * @property hrM2 Suma de diferencias al cuadrado de HR.
 * @property hrvCount Cantidad de muestras de HRV acumuladas.
 * @property hrvMean Media acumulada de HRV.
 * @property hrvM2 Suma de diferencias al cuadrado de HRV.
 * @property calibrationWindows Número de ventanas de baseline completadas.
 */
data class BaselineData(
    val hrCount:            Int,
    val hrMean:             Double,
    val hrM2:               Double,
    val hrvCount:           Int,
    val hrvMean:            Double,
    val hrvM2:              Double,
    val calibrationWindows: Int
)