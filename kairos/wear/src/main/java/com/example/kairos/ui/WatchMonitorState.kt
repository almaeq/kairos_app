package com.example.kairos.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de detección del sistema KAIROS en el reloj en un momento dado.
 *
 * Equivalente Wear OS de [CrisisState] del teléfono.
 * Se usa para determinar qué pantalla mostrar en [MainActivity] del reloj
 * y qué mensaje enviar al teléfono via Wearable Message API.
 */
enum class WatchCrisisState { NORMAL, PRE_ALERT, CRISIS }

/**
 * Snapshot inmutable del estado de monitoreo del reloj en un momento dado.
 *
 * Equivalente Wear OS de [MonitorData] del teléfono.
 * Al ser un data class inmutable, cada actualización genera una nueva instancia,
 * garantizando que el [StateFlow] emita siempre ante cualquier cambio de valor.
 *
 * @property heartRate Frecuencia cardíaca más reciente del sensor, en BPM.
 *           Valor 0.0 indica que el sensor aún no entregó datos.
 * @property rmssd RMSSD más reciente calculado sobre la ventana actual, en milisegundos.
 *           Valor 0.0 indica que aún no hay suficientes muestras para calcularlo.
 * @property crisisState Estado actual del pipeline de detección en el reloj.
 * @property calibrationWindows Número de ventanas de baseline completadas.
 *           Se muestra en la UI como "N/3" durante la calibración.
 * @property isCalibrated `true` si se completaron al menos 3 ventanas de calibración.
 *           Cuando es `false`, el detector usa los umbrales globales WESAD como fallback.
 */
data class WatchMonitorData(
    val heartRate:          Double          = 0.0,
    val rmssd:              Double          = 0.0,
    val crisisState:        WatchCrisisState = WatchCrisisState.NORMAL,
    val calibrationWindows: Int             = 0,
    val isCalibrated:       Boolean         = false
)

/**
 * Fuente de verdad global del estado de monitoreo del reloj.
 *
 * Centraliza todas las actualizaciones de estado que provienen de
 * [KairosPassiveListener] y [KairosWatchService], y las expone como [StateFlow]
 * para que [MainActivity] del reloj reaccione automáticamente sin polling.
 *
 * Todas las actualizaciones son thread-safe por la naturaleza atómica de las
 * asignaciones a [MutableStateFlow.value].
 */
object WatchMonitorState {

    private val _state = MutableStateFlow(WatchMonitorData())

    /**
     * Estado de monitoreo actual como Flow de solo lectura.
     * La UI del reloj observa este Flow para actualizar el indicador de HR,
     * el estado de crisis y el progreso de calibración.
     */
    val state: StateFlow<WatchMonitorData> = _state

    /**
     * Actualiza el estado completo de monitoreo con los resultados del último análisis.
     *
     * Se invoca en cada ciclo de análisis de [KairosPassiveListener] y [KairosWatchService]
     * tras procesar una ventana de HR. Reemplaza todo el [WatchMonitorData] para garantizar
     * consistencia entre los campos relacionados.
     *
     * @param heartRate HR media de la última ventana analizada, en BPM.
     * @param rmssd RMSSD de la última ventana analizada, en milisegundos.
     * @param crisisState Resultado del pipeline de detección para esta ventana.
     * @param calibrationWindows Número de ventanas de baseline completadas hasta el momento.
     */
    fun update(
        heartRate:          Double,
        rmssd:              Double,
        crisisState:        WatchCrisisState,
        calibrationWindows: Int
    ) {
        _state.value = WatchMonitorData(
            heartRate          = heartRate,
            rmssd              = rmssd,
            crisisState        = crisisState,
            calibrationWindows = calibrationWindows,
            isCalibrated       = calibrationWindows >= 3
        )
    }

    // Para cuando termina un ejercicio o se cancela una crisis — NO toca calibración
    fun resetCrisisState() {
        _state.value = _state.value.copy(
            heartRate   = 0.0,
            rmssd       = 0.0,
            crisisState = WatchCrisisState.NORMAL
        )
    }

    // Para cuando el usuario realmente recalibra — sí resetea todo
    fun resetFull() {
        _state.value = WatchMonitorData()
    }
}