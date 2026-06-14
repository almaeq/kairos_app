package com.example.kairos.mobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de detección del sistema KAIROS en un momento dado.
 *
 * Representa la máquina de estados del pipeline de detección:
 * - [NORMAL]: monitoreo pasivo sin anomalías detectadas.
 * - [PRE_ALERT]: el modelo superó [CrisisPredictor.THRESHOLD_PRE_ALERT]
 *   pero todavía no alcanzó el umbral de crisis. Se muestra la pantalla
 *   "¿Estás bien?" para que el usuario pueda cancelar si es un falso positivo.
 * - [CRISIS]: el modelo superó [CrisisPredictor.THRESHOLD_CRISIS] durante
 *   [WesadThresholds.CONSECUTIVE_WINDOWS_TO_CONFIRM] ventanas consecutivas.
 *   Se activa el Modo Crisis completo (SMS + ejercicios).
 */
enum class CrisisState { NORMAL, PRE_ALERT, CRISIS }

/**
 * Snapshot inmutable del estado de monitoreo en un momento dado.
 *
 * Es el modelo de datos que la UI observa via [MonitorState.data].
 * Al ser un data class inmutable, cada actualización genera una nueva instancia,
 * garantizando que los StateFlow emitan siempre ante cualquier cambio.
 *
 * @property heartRate Frecuencia cardíaca más reciente recibida desde el reloj, en BPM.
 *           Valor 0.0 indica que aún no se recibió ningún dato.
 * @property rmssd RMSSD más reciente recibido desde el reloj, en milisegundos.
 *           Valor 0.0 indica que aún no se recibió ningún dato.
 * @property crisisState Estado actual del pipeline de detección.
 * @property calibrationWindows Número de ventanas de baseline completadas.
 *           Se usa para mostrar el progreso de calibración en la UI.
 * @property isCalibrated `true` si se completaron al menos 3 ventanas de calibración.
 *           Cuando es `false`, la detección usa los umbrales globales WESAD como fallback.
 * @property lastUpdated Timestamp del último dato recibido desde el reloj, en milisegundos.
 *           Valor 0L indica que aún no se recibió ningún dato.
 * @property watchConnected Indica si el reloj está actualmente conectado al teléfono.
 */
data class MonitorData(
    val heartRate:          Double      = 0.0,
    val rmssd:              Double      = 0.0,
    val crisisState:        CrisisState = CrisisState.NORMAL,
    val calibrationWindows: Int         = 0,
    val isCalibrated:       Boolean     = false,
    val lastUpdated:        Long        = 0L,
    val watchConnected:     Boolean     = false
)

/**
 * Fuente de verdad global del estado de monitoreo de KAIROS en el teléfono.
 *
 * Centraliza todas las actualizaciones de estado que provienen del reloj
 * ([KairosPhoneListener]) y las expone como [StateFlow] para que la UI
 * de Compose reaccione automáticamente sin polling.
 *
 * Todas las funciones de actualización son thread-safe por la naturaleza
 * atómica de las asignaciones a [MutableStateFlow.value].
 */
object MonitorState {

    private val _data = MutableStateFlow(MonitorData())

    /**
     * Estado de monitoreo actual como Flow de solo lectura.
     * La UI observa este Flow para actualizar la pantalla principal,
     * el indicador de HR y el estado de calibración.
     */
    val data: StateFlow<MonitorData> = _data

    /**
     * Precarga el estado de calibración desde Room al arrancar la app.
     *
     * Permite mostrar el estado de calibración correcto inmediatamente al abrir
     * la app, sin esperar el primer heartbeat del reloj (que puede tardar hasta 60s).
     *
     * No modifica [MonitorData.heartRate] ni [MonitorData.lastUpdated] para no
     * simular datos frescos cuando el reloj aún no envió ningún dato.
     *
     * @param calibrationWindows Número de ventanas de calibración completadas,
     *        leído desde [BaselineStats] en Room.
     */
    fun preloadCalibration(calibrationWindows: Int) {
        _data.value = _data.value.copy(
            calibrationWindows = calibrationWindows,
            isCalibrated       = calibrationWindows >= 3
        )
    }

    /**
     * Actualiza únicamente la frecuencia cardíaca.
     *
     * Se usa cuando el reloj envía un mensaje `/kairos/hr` puntual,
     * sin actualizar el estado de crisis ni la calibración.
     *
     * @param bpm Nueva frecuencia cardíaca en BPM.
     */
    fun updateHr(bpm: Double) {
        _data.value = _data.value.copy(
            heartRate   = bpm,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Actualiza el estado completo de monitoreo con datos recibidos desde el reloj.
     *
     * Se invoca para cada mensaje de heartbeat, pre-alerta o crisis recibido
     * desde [KairosPhoneListener]. Reemplaza todo el [MonitorData] para garantizar
     * consistencia entre los campos relacionados.
     *
     * @param bpm Frecuencia cardíaca en BPM.
     * @param rmssd RMSSD en milisegundos.
     * @param state Estado del pipeline de detección.
     * @param calibrationWindows Número de ventanas de calibración completadas.
     */
    fun updateFromWear(
        bpm:                Double,
        rmssd:              Double,
        state:              CrisisState,
        calibrationWindows: Int
    ) {
        _data.value = MonitorData(
            heartRate          = bpm,
            rmssd              = rmssd,
            crisisState        = state,
            calibrationWindows = calibrationWindows,
            isCalibrated       = calibrationWindows >= 3,
            lastUpdated        = System.currentTimeMillis(),
            watchConnected     = true
        )
    }

    /**
     * Actualiza el estado de conexión del reloj.
     *
     * Se invoca desde [KairosPhoneListener.onPeerConnected] y
     * [KairosPhoneListener.onPeerDisconnected] cuando cambia la conectividad Bluetooth.
     *
     * @param connected `true` si el reloj está conectado, `false` si se desconectó.
     */
    fun setWatchConnected(connected: Boolean) {
        _data.value = _data.value.copy(watchConnected = connected)
    }

    /**
     * Establece el estado de crisis directamente.
     * Se usa cuando la confirmación de crisis llega por un canal distinto al heartbeat.
     */
    fun setCrisis()   { _data.value = _data.value.copy(crisisState = CrisisState.CRISIS) }

    /**
     * Establece el estado de pre-alerta directamente.
     * Se usa para actualizar la UI antes de recibir el próximo heartbeat completo.
     */
    fun setPreAlert() { _data.value = _data.value.copy(crisisState = CrisisState.PRE_ALERT) }

    /**
     * Restablece el estado normal de monitoreo.
     * Se usa cuando el usuario cancela una crisis o cuando llega un heartbeat sin anomalías.
     */
    fun setNormal()   { _data.value = _data.value.copy(crisisState = CrisisState.NORMAL) }
}