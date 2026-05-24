package com.example.kairos.mobile.techniques

data class BreathingPhase(
    val label:      String,
    val durationMs: Long
)

val BREATHING_PHASES = listOf(
    BreathingPhase("Inhalá",  4_000L),
    BreathingPhase("Retené",  4_000L),
    BreathingPhase("Exhalá",  4_000L),
    BreathingPhase("Retené",  4_000L)
)

const val BREATHING_TOTAL_CYCLES = 6
val BREATHING_TOTAL_MS = BREATHING_TOTAL_CYCLES * BREATHING_PHASES.sumOf { it.durationMs }