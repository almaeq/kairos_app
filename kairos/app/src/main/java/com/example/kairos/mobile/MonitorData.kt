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
    val lastUpdated: Long = 0L
)

object MonitorState {
    private val _data = MutableStateFlow(MonitorData())
    val data: StateFlow<MonitorData> = _data

    fun updateHr(bpm: Double) {
        _data.value = _data.value.copy(
            heartRate = bpm,
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
            heartRate = bpm,
            rmssd = rmssd,
            crisisState = state,
            calibrationWindows = calibrationWindows,
            isCalibrated = calibrationWindows >= 3,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun setCrisis() {
        _data.value = _data.value.copy(crisisState = CrisisState.CRISIS)
    }

    fun setPreAlert() {
        _data.value = _data.value.copy(crisisState = CrisisState.PRE_ALERT)
    }

    fun setNormal() {
        _data.value = _data.value.copy(crisisState = CrisisState.NORMAL)
    }
}