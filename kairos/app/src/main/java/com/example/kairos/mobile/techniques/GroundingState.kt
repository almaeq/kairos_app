package com.example.kairos.mobile.techniques

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object GroundingState {
    // Paso actual: 5,4,3,2,1 = paso activo | -1 = no iniciado | 0 = terminado
    private val _currentStep = MutableStateFlow(-1)
    val currentStep: StateFlow<Int> = _currentStep

    fun updateStep(step: Int) {
        _currentStep.value = step
    }

    fun reset() {
        _currentStep.value = -1
    }
}