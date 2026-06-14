package com.example.kairos.mobile.techniques

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado observable del ejercicio de respiración guiada en curso.
 *
 * Expone el progreso del ejercicio como [StateFlow] para que la UI de Compose
 * reaccione automáticamente a cada cambio de fase o ciclo sin polling explícito.
 *
 * **Ciclo de vida del estado:**
 * ```
 * "" (no iniciado) → "Inhalá" / "Retené" / "Exhalá" → "done" (terminado)
 *                                    ↑ reset() ↓
 *                               "" (reiniciado)
 * ```
 *
 * El valor `"done"` es el estado terminal del ejercicio — indica que se completaron
 * todos los ciclos y la UI puede mostrar el mensaje de finalización.
 * El valor `""` indica que el ejercicio no ha comenzado o fue reiniciado.
 */
object BreathingState {

    /**
     * Fase de respiración actualmente en curso.
     *
     * Valores posibles:
     * - `""` — ejercicio no iniciado o reiniciado.
     * - `"Inhalá"`, `"Retené"`, `"Exhalá"` — fase activa del ciclo actual.
     * - `"done"` — ejercicio completado (todos los ciclos finalizados).
     */
    private val _currentPhase = MutableStateFlow("")

    /**
     * Versión pública de solo lectura de [_currentPhase].
     * La UI observa este Flow para actualizar la instrucción visible al usuario.
     */
    val currentPhase: StateFlow<String> = _currentPhase

    /**
     * Número del ciclo de respiración actualmente en curso (rango: 1 a [BREATHING_TOTAL_CYCLES]).
     * El valor 0 indica que el ejercicio no ha comenzado.
     */
    private val _currentCycle = MutableStateFlow(0)

    /**
     * Versión pública de solo lectura de [_currentCycle].
     * La UI observa este Flow para mostrar el progreso ("Ciclo 3 de 6").
     */
    val currentCycle: StateFlow<Int> = _currentCycle

    /**
     * Actualiza la fase y el ciclo actuales del ejercicio.
     *
     * Se invoca en cada transición de fase dentro del loop de respiración.
     * Ambos valores se actualizan juntos para mantener consistencia entre
     * la instrucción visible y el contador de ciclos.
     *
     * @param phase Etiqueta de la fase actual ("Inhalá", "Retené" o "Exhalá").
     * @param cycle Número del ciclo actual (1 a [BREATHING_TOTAL_CYCLES]).
     */
    fun updatePhase(phase: String, cycle: Int) {
        _currentPhase.value = phase
        _currentCycle.value = cycle
    }

    /**
     * Marca el ejercicio como completado.
     *
     * Establece [currentPhase] en `"done"` para señalar a la UI que todos los
     * ciclos finalizaron y puede mostrar el mensaje de cierre del Modo Crisis.
     */
    fun markDone() {
        _currentPhase.value = "done"
    }

    /**
     * Reinicia el estado del ejercicio a sus valores iniciales.
     *
     * Se invoca cuando el usuario cancela el ejercicio manualmente o cuando
     * se inicia una nueva sesión de Modo Crisis. Deja el tracker listo para
     * comenzar desde el primer ciclo.
     */
    fun reset() {
        _currentPhase.value = ""
        _currentCycle.value = 0
    }
}