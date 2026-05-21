package com.example.kairos.mobile

import android.content.Context

enum class ExerciseMode {
    BREATHING_ONLY,   // Solo respiración 4-4-4
    GROUNDING_ONLY,   // Solo grounding 5-4-3-2-1
    BOTH_SEQUENCE     // Ambas en secuencia (4-4-4 → 5-4-3-2-1) — recomendado
}

object ExercisePreference {

    private const val PREFS_NAME = "kairos_exercise_prefs"
    private const val KEY_MODE   = "exercise_mode"

    fun save(context: Context, mode: ExerciseMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun load(context: Context): ExerciseMode {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ExerciseMode.BOTH_SEQUENCE.name)
        return try {
            ExerciseMode.valueOf(saved ?: ExerciseMode.BOTH_SEQUENCE.name)
        } catch (e: Exception) {
            ExerciseMode.BOTH_SEQUENCE
        }
    }
}