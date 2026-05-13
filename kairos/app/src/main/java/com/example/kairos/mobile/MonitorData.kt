package com.example.kairos.mobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class CrisisState { NORMAL, PRE_ALERT, CRISIS }

data class MonitorData(
    val heartRate: Double = 0.0,
    val rmssd: Double = 0.0,
    val crisisState: CrisisState = CrisisState.NORMAL,
    val calibrationWindows: Int = 0,
    val isCalibrated: Boolean = false,
    val lastUpdated: Long = 0L,
    val watchConnected: Boolean = false
)

object MonitorState {
    private val _data = MutableStateFlow(MonitorData())
    val data: StateFlow<MonitorData> = _data

    /**
     * Llamado al arrancar la app para mostrar el estado de calibración
     * correcto desde Room, sin esperar el primer heartbeat del reloj.
     * No toca heartRate ni lastUpdated para no fingir datos frescos.
     */
    fun preloadCalibration(calibrationWindows: Int) {
        _data.value = _data.value.copy(
            calibrationWindows = calibrationWindows,
            isCalibrated       = calibrationWindows >= 3
        )
    }

    fun updateHr(bpm: Double) {
        _data.value = _data.value.copy(
            heartRate   = bpm,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun updateFromWear(
        bpm: Double,
        rmssd: Double,
        state: CrisisState,
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

    fun setWatchConnected(connected: Boolean) {
        _data.value = _data.value.copy(watchConnected = connected)
    }

    fun setCrisis()   { _data.value = _data.value.copy(crisisState = CrisisState.CRISIS) }
    fun setPreAlert() { _data.value = _data.value.copy(crisisState = CrisisState.PRE_ALERT) }
    fun setNormal()   { _data.value = _data.value.copy(crisisState = CrisisState.NORMAL) }
}