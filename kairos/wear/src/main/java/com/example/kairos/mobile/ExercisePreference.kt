package com.example.kairos.mobile

import android.content.Context

/**
 * Modos de intervención disponibles durante el Modo Crisis en el reloj.
 *
 * Determina qué ejercicios se ejecutan automáticamente cuando [WatchCrisisDetector]
 * confirma una crisis. La preferencia es configurada por el usuario en el teléfono
 * y sincronizada al reloj via Wearable DataClient.
 *
 * Equivalente Wear OS del enum [ExercisePreference] del teléfono, con nombres
 * de valores distintos por contexto de plataforma.
 */
enum class ExerciseMode {
    /** Ejecuta únicamente el ejercicio de respiración box (4-4-4-4). */
    BREATHING_ONLY,

    /** Ejecuta únicamente el ejercicio de grounding 5-4-3-2-1. */
    GROUNDING_ONLY,

    /**
     * Ejecuta ambas técnicas en secuencia: primero respiración 4-4-4-4,
     * luego grounding 5-4-3-2-1. Es la opción por defecto y la recomendada
     * porque combina regulación fisiológica con anclaje cognitivo.
     */
    BOTH_SEQUENCE
}

/**
 * Gestiona la persistencia local de la preferencia de ejercicio en el reloj.
 *
 * La preferencia se guarda en SharedPreferences del reloj como respaldo local,
 * ya que el valor primario llega sincronizado desde el teléfono via Wearable DataClient
 * (path `/kairos/exercise_preference`). Si no llega sincronización, este valor local
 * actúa como fallback.
 */
object ExercisePreference {

    private const val PREFS_NAME = "kairos_exercise_prefs"
    private const val KEY_MODE   = "exercise_mode"

    /**
     * Persiste la preferencia de ejercicio en SharedPreferences del reloj.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @param mode Modo de ejercicio seleccionado.
     */
    fun save(context: Context, mode: ExerciseMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /**
     * Carga la preferencia de ejercicio desde SharedPreferences del reloj.
     *
     * Si el valor guardado no corresponde a ningún [ExerciseMode] conocido
     * (por ejemplo, tras una actualización de la app que renombró un valor),
     * retorna [ExerciseMode.BOTH_SEQUENCE] como fallback seguro.
     *
     * @param context Contexto para acceder a SharedPreferences.
     * @return Modo de ejercicio guardado, o [ExerciseMode.BOTH_SEQUENCE] si no hay valor válido.
     */
    fun load(context: Context): ExerciseMode {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ExerciseMode.BOTH_SEQUENCE.name)
        return try {
            ExerciseMode.valueOf(saved ?: ExerciseMode.BOTH_SEQUENCE.name)
        } catch (e: Exception) {
            // valueOf lanza IllegalArgumentException si el string no coincide con ningún valor del enum
            ExerciseMode.BOTH_SEQUENCE
        }
    }
}