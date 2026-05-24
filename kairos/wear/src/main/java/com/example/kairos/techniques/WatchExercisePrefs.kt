package com.example.kairos.techniques

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ExercisePreference(val key: String) {
    BREATHING_ONLY("breathing_only"),
    GROUNDING_ONLY("grounding_only"),
    BOTH("both")
}

object WatchExercisePrefs {

    private const val PREFS = "kairos_exercise_prefs"
    private const val KEY   = "exercise_preference"

    // StateFlow observable desde MainActivity
    // Se actualiza cuando KairosDataListener recibe sync del teléfono
    private val _preference = MutableStateFlow(ExercisePreference.BOTH)
    val preference: StateFlow<ExercisePreference> = _preference

    fun load(context: Context): ExercisePreference {
        val key  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ExercisePreference.BOTH.key)
        val pref = ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
        _preference.value = pref
        return pref
    }

    fun save(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, key).apply()
        val pref = ExercisePreference.entries.find { it.key == key } ?: ExercisePreference.BOTH
        _preference.value = pref
        Log.d("WatchExercisePrefs", "Preferencia: $pref")
    }
}