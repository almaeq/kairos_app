package com.example.kairos.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MindfulnessSessionRecord
import com.example.kairos.techniques.ExercisePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Coordina el registro de la sesión de intervención en Health Connect.
 *
 * Lógica:
 * - BOTH           → una sola sesión desde inicio de respiración hasta fin de grounding
 * - BREATHING_ONLY → sesión individual de respiración
 * - GROUNDING_ONLY → sesión individual de grounding
 */
object InterventionSession {

    private const val TAG = "InterventionSession"

    private var sessionStartMs = 0L
    private var breathingStartMs = 0L
    private var currentPref: ExercisePreference = ExercisePreference.BOTH
    private var crisisHr: Double = 0.0

    /**
     * Llamar cuando empieza el primer ejercicio (justo después del countdown).
     */
    fun onExerciseStarted(pref: ExercisePreference, heartRate: Double) {
        sessionStartMs   = System.currentTimeMillis()
        breathingStartMs = sessionStartMs
        currentPref      = pref
        crisisHr         = heartRate
        Log.d(TAG, "Sesión iniciada — pref=$pref HR=$heartRate")
    }

    /**
     * Llamar cuando termina la respiración.
     * En BOTH, no cierra la sesión — espera al grounding.
     * En BREATHING_ONLY, cierra y registra.
     */
    @OptIn(ExperimentalMindfulnessSessionApi::class)
    fun onBreathingFinished(context: Context) {
        if (currentPref == ExercisePreference.BREATHING_ONLY) {
            val endMs = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch {
                MindfulnessSessionManager.record(
                    context     = context,
                    startTime   = sessionStartMs,
                    endTime     = endMs,
                    sessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                    title       = "KAIROS — Respiración 4-4-4",
                    notes       = "Intervención post-crisis. HR al inicio: ${"%.0f".format(crisisHr)} BPM."
                )
            }
            reset()
        }
        // En BOTH, no hacer nada — la sesión sigue abierta hasta el grounding
        Log.d(TAG, "Respiración terminada — pref=$currentPref")
    }

    /**
     * Llamar cuando termina el grounding.
     * En GROUNDING_ONLY, registra solo el grounding.
     * En BOTH, registra la sesión combinada completa.
     */
    @OptIn(ExperimentalMindfulnessSessionApi::class)
    fun onGroundingFinished(context: Context) {
        val endMs = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            when (currentPref) {
                ExercisePreference.GROUNDING_ONLY -> {
                    MindfulnessSessionManager.record(
                        context     = context,
                        startTime   = sessionStartMs,
                        endTime     = endMs,
                        sessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                        title       = "KAIROS — Grounding 5-4-3-2-1",
                        notes       = "Intervención post-crisis. HR al inicio: ${"%.0f".format(crisisHr)} BPM."
                    )
                }
                ExercisePreference.BOTH -> {
                    MindfulnessSessionManager.record(
                        context     = context,
                        startTime   = sessionStartMs,
                        endTime     = endMs,
                        sessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                        title       = "KAIROS — Respiración + Grounding",
                        notes       = "Intervención completa post-crisis. HR al inicio: ${"%.0f".format(crisisHr)} BPM."
                    )
                }
                else -> { /* BREATHING_ONLY ya se registró en onBreathingFinished */ }
            }
        }
        reset()
        Log.d(TAG, "Grounding terminado — sesión cerrada")
    }

    private fun reset() {
        sessionStartMs   = 0L
        breathingStartMs = 0L
    }
}