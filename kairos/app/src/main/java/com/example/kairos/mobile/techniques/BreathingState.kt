package com.example.kairos.mobile.techniques

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BreathingState {
    // Fase actual: label de la fase ("Inhalá", "Retené", "Exhalá") o "" = no iniciado | "done" = terminado
    private val _currentPhase = MutableStateFlow("")
    val currentPhase: StateFlow<String> = _currentPhase

    // Ciclo actual (1..6)
    private val _currentCycle = MutableStateFlow(0)
    val currentCycle: StateFlow<Int> = _currentCycle

    fun updatePhase(phase: String, cycle: Int) {
        _currentPhase.value = phase
        _currentCycle.value = cycle
    }

    fun markDone() {
        _currentPhase.value = "done"
    }

    fun reset() {
        _currentPhase.value = ""
        _currentCycle.value = 0
    }
}