package com.example.kairos.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WatchCrisisState { NORMAL, PRE_ALERT, CRISIS }

data class WatchMonitorData(
    val heartRate: Double = 0.0,
    val rmssd: Double = 0.0,
    val crisisState: WatchCrisisState = WatchCrisisState.NORMAL,
    val calibrationWindows: Int = 0,
    val isCalibrated: Boolean = false
)

object WatchMonitorState {
    private val _state = MutableStateFlow(WatchMonitorData())
    val state: StateFlow<WatchMonitorData> = _state

    fun update(
        heartRate: Double,
        rmssd: Double,
        crisisState: WatchCrisisState,
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

    // Vuelve al estado inicial para que la UI muestre "0/3" inmediatamente
    fun reset() {
        _state.value = WatchMonitorData()
    }
}