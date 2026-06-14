package com.example.kairos.mobile.techniques

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado observable del ejercicio de grounding 5-4-3-2-1 en curso.
 *
 * Expone el progreso del ejercicio como [StateFlow] para que la UI de Compose
 * reaccione automáticamente a cada cambio de paso sin polling explícito.
 *
 * **Ciclo de vida del estado:**
 * ```
 * -1 (no iniciado) → 5 → 4 → 3 → 2 → 1 → 0 (terminado)
 *          ↑                                       |
 *          └──────────── reset() ─────────────────┘
 * ```
 *
 * El valor representa el número de estímulos a identificar en el paso actual,
 * lo que permite a la UI mostrar directamente el número sin mapeo adicional.
 */
object GroundingState {

    /**
     * Paso de grounding actualmente en curso.
     *
     * Valores posibles:
     * - `-1` — ejercicio no iniciado o reiniciado.
     * - `5`, `4`, `3`, `2`, `1` — paso activo (número de estímulos a identificar).
     * - `0` — ejercicio completado (todos los pasos finalizados).
     */
    private val _currentStep = MutableStateFlow(-1)

    /**
     * Versión pública de solo lectura de [_currentStep].
     * La UI observa este Flow para mostrar el paso activo y actualizar
     * la instrucción visible al usuario.
     */
    val currentStep: StateFlow<Int> = _currentStep

    /**
     * Actualiza el paso activo del ejercicio de grounding.
     *
     * Se invoca en cada transición de paso dentro del loop del ejercicio.
     * Usar `0` para indicar que el ejercicio finalizó.
     *
     * @param step Número del paso activo (5, 4, 3, 2, 1) o `0` si el ejercicio terminó.
     */
    fun updateStep(step: Int) {
        _currentStep.value = step
    }

    /**
     * Reinicia el estado del ejercicio a su valor inicial (-1).
     *
     * Se invoca cuando el usuario cancela el ejercicio manualmente o cuando
     * se inicia una nueva sesión de Modo Crisis. Deja el tracker listo para
     * comenzar desde el primer paso.
     */
    fun reset() {
        _currentStep.value = -1
    }
}