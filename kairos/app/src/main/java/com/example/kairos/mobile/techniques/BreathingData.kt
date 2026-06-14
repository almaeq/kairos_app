package com.example.kairos.mobile.techniques

/**
 * Representa una fase individual de un ciclo de respiración guiada.
 *
 * Cada fase tiene una instrucción verbal y una duración específica.
 * Las fases se ejecutan secuencialmente para formar un ciclo completo.
 *
 * @property label Instrucción a mostrar al usuario durante esta fase
 *           (por ejemplo: "Inhalá", "Retené", "Exhalá").
 * @property durationMs Duración de la fase en milisegundos.
 */
data class BreathingPhase(
    val label:      String,
    val durationMs: Long
)

/**
 * Secuencia de fases que componen un ciclo de respiración box (respiración cuadrada).
 *
 * La técnica de respiración box (4-4-4-4) es una intervención basada en la
 * Teoría Polivagal: la exhalación prolongada estimula el nervio vago y activa
 * el sistema nervioso parasimpático, interrumpiendo el bucle fisiológico del pánico.
 *
 * Cada fase dura 4 segundos, formando un ciclo completo de 16 segundos:
 * - **Inhalá (4s):** expansión diafragmática, aumento de oxígeno.
 * - **Retené (4s):** pausa inspiratoria, estabiliza el CO₂.
 * - **Exhalá (4s):** activa el sistema parasimpático via nervio vago.
 * - **Retené (4s):** pausa espiratoria, prolonga el efecto calmante.
 */
val BREATHING_PHASES = listOf(
    BreathingPhase("Inhalá", 4_000L),
    BreathingPhase("Retené", 4_000L),
    BreathingPhase("Exhalá", 4_000L),
    BreathingPhase("Retené", 4_000L)
)

/**
 * Número total de ciclos de respiración box que componen el ejercicio completo.
 *
 * 6 ciclos × 16 segundos = 96 segundos (~1.5 minutos) de respiración guiada.
 * Este tiempo es suficiente para que el sistema parasimpático tome predominio
 * y la frecuencia cardíaca comience a estabilizarse.
 */
const val BREATHING_TOTAL_CYCLES = 6

/**
 * Duración total del ejercicio de respiración en milisegundos.
 *
 * Calculado como: [BREATHING_TOTAL_CYCLES] × suma de duraciones de [BREATHING_PHASES].
 * Se usa para mostrar el progreso total del ejercicio en la UI y para
 * determinar cuándo finaliza automáticamente el Modo Crisis.
 */
val BREATHING_TOTAL_MS = BREATHING_TOTAL_CYCLES * BREATHING_PHASES.sumOf { it.durationMs }