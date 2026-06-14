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
 * Coordina el registro de sesiones de intervención en Health Connect
 * como [MindfulnessSessionRecord].
 *
 * Gestiona el ciclo de vida de la sesión según la preferencia de ejercicio del usuario,
 * determinando cuándo abrir y cerrar cada registro en Health Connect:
 *
 * | Preferencia | Comportamiento |
 * |-------------|----------------|
 * | [ExercisePreference.BREATHING_ONLY] | Una sesión de respiración, se cierra al terminar la respiración |
 * | [ExercisePreference.GROUNDING_ONLY] | Una sesión de grounding, se cierra al terminar el grounding |
 * | [ExercisePreference.BOTH] | Una sola sesión combinada desde el inicio de la respiración hasta el fin del grounding |
 *
 * **Limitación documentada:**
 * `MindfulnessSessionRecord` falla en el Pixel Watch 3 por restricción de versión del SDK
 * de Health Connect. Esta clase está implementada correctamente pero el registro
 * efectivo está bloqueado por hardware — documentado como limitación conocida del MVP.
 */
object InterventionSession {

    private const val TAG = "InterventionSession"

    /** Timestamp de inicio del primer ejercicio de la sesión, en milisegundos. */
    private var sessionStartMs = 0L

    /** Timestamp de inicio específico de la respiración (reservado para uso futuro). */
    private var breathingStartMs = 0L

    /** Preferencia de ejercicio activa en la sesión actual. */
    private var currentPref: ExercisePreference = ExercisePreference.BOTH

    /** HR registrada al inicio de la crisis, incluida en las notas del registro. */
    private var crisisHr: Double = 0.0

    /**
     * Registra el inicio de la sesión de intervención.
     *
     * Debe llamarse justo después del countdown, cuando comienza el primer ejercicio.
     * Captura el timestamp de inicio y la HR de crisis para incluirla en el registro
     * de Health Connect como contexto clínico.
     *
     * @param pref Preferencia de ejercicio configurada por el usuario.
     * @param heartRate HR en BPM registrada al momento de la detección de la crisis.
     */
    fun onExerciseStarted(pref: ExercisePreference, heartRate: Double) {
        sessionStartMs   = System.currentTimeMillis()
        breathingStartMs = sessionStartMs
        currentPref      = pref
        crisisHr         = heartRate
        Log.d(TAG, "Sesión iniciada — pref=$pref HR=$heartRate")
    }

    /**
     * Notifica que el ejercicio de respiración finalizó.
     *
     * El comportamiento varía según la preferencia activa:
     * - [ExercisePreference.BREATHING_ONLY]: cierra y registra la sesión en Health Connect.
     * - [ExercisePreference.BOTH]: no hace nada — la sesión permanece abierta
     *   hasta que finalice el grounding en [onGroundingFinished].
     *
     * @param context Contexto para acceder al cliente de Health Connect.
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
        // En BOTH: la sesión sigue abierta — se cerrará en onGroundingFinished
        Log.d(TAG, "Respiración terminada — pref=$currentPref")
    }

    /**
     * Notifica que el ejercicio de grounding finalizó y cierra la sesión en Health Connect.
     *
     * El comportamiento varía según la preferencia activa:
     * - [ExercisePreference.GROUNDING_ONLY]: registra una sesión de grounding individual.
     * - [ExercisePreference.BOTH]: registra una sesión combinada que abarca toda la intervención
     *   (desde el inicio de la respiración hasta el fin del grounding).
     * - [ExercisePreference.BREATHING_ONLY]: no hace nada — la sesión ya fue registrada
     *   en [onBreathingFinished].
     *
     * @param context Contexto para acceder al cliente de Health Connect.
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
                    // Sesión combinada: cubre respiración + grounding como una unidad
                    MindfulnessSessionManager.record(
                        context     = context,
                        startTime   = sessionStartMs,
                        endTime     = endMs,
                        sessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                        title       = "KAIROS — Respiración + Grounding",
                        notes       = "Intervención completa post-crisis. HR al inicio: ${"%.0f".format(crisisHr)} BPM."
                    )
                }
                else -> { /* BREATHING_ONLY ya fue registrado en onBreathingFinished */ }
            }
        }
        reset()
        Log.d(TAG, "Grounding terminado — sesión cerrada")
    }

    /**
     * Resetea el estado interno de la sesión al finalizar un ciclo de intervención.
     */
    private fun reset() {
        sessionStartMs   = 0L
        breathingStartMs = 0L
    }
}